package main;

import data.satellite.*;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solution;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class TransmissionScheduler {

    // Repère temporel commun : début de la journée du 8 février 2023.
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2023, 2, 8, 0, 0);
    private static final double EFFECTIVE_RATE = 10.0;

    public static void main(String[] args) throws Exception {
        long startTimeMillis = System.currentTimeMillis();
        TransmissionInstance instance = Utils.fromFile("data/transmission/transmission_a.json", TransmissionInstance.class);
        if (instance == null) {
            System.err.println("Erreur lors de la lecture du fichier JSON.");
            return;
        }

        Model model = new Model("Planification Téléchargement Satellite");

        // maps pour stocker les variables associées à chaque fichier
        Map<String, IntVar> startVars = new HashMap<>();
        Map<String, IntVar> endVars = new HashMap<>();
        Map<String, IntVar> windowChoiceVars = new HashMap<>();
        Map<String, IntVar> stationAssignedVars = new HashMap<>();
        // stockage des durées calculées pour chaque fichier
        Map<String, Integer> durationMap = new HashMap<>();

        // création d'une table de correspondance stationId -> indice (pour modéliser la capacité des stations)
        Station[] stations = instance.getStations();
        Map<String, Integer> stationIndex = new HashMap<>();
        for (int i = 0; i < stations.length; i++) {
            stationIndex.put(stations[i].getId(), i);
        }

        // pour la contrainte cumulative sur les satellites, on regroupe par satellite
        Map<String, List<IntVar>> satStartMap = new HashMap<>();
        Map<String, List<Integer>> satDurationsMap = new HashMap<>();

        // calcul de l'horizon temporel : fin maximale d'une fenêtre sur tous les satellites
        int horizon = 0;
        for (Satellite sat : instance.getSatellites()) {
            for (TransmissionWindow tw : sat.getTransmissionWindows()) {
                int end = toMinutes(tw.getEnd());
                if (end > horizon) {
                    horizon = end;
                }
            }
        }

        // pour la modélisation de la capacité des stations, on utilisera une discrétisation avec un pas toutes les 5 minutes
        int timeStep = 5;

        // itération sur chaque satellite
        for (Satellite satellite : instance.getSatellites()) {
            List<IntVar> satStarts = new ArrayList<>();
            List<Integer> satDurations = new ArrayList<>();

            TransmissionWindow[] windows = satellite.getTransmissionWindows();
            for (data.satellite.File file : satellite.getFiles()) {
                int releaseTime = toMinutes(file.getReleaseDateTime());
                // calcul de la durée
                int duration = (int) Math.ceil(file.getSize() / EFFECTIVE_RATE);
                durationMap.put(file.getId(), duration);

                // pour éviter le problème de domaine, on définit la borne supérieure comme max(horizon, releaseTime)
                int fileStartUpperBound = Math.max(horizon, releaseTime);
                IntVar start = model.intVar("start_" + file.getId(), releaseTime, fileStartUpperBound);
                IntVar end = model.intVar("end_" + file.getId(), releaseTime + duration, fileStartUpperBound + duration);
                model.arithm(end, "=", start, "+", duration).post();

                startVars.put(file.getId(), start);
                endVars.put(file.getId(), end);
                satStarts.add(start);
                satDurations.add(duration);

                // variable windowChoice : indice de la fenêtre choisie parmi celles du satellite
                IntVar windowChoice = model.intVar("window_" + file.getId(), 0, windows.length - 1);
                windowChoiceVars.put(file.getId(), windowChoice);

                // association de la fenêtre à une station via un tableau de correspondance
                int[] windowMapping = new int[windows.length];
                Set<Integer> possibleStations = new HashSet<>();
                for (int i = 0; i < windows.length; i++) {
                    int sIndex = stationIndex.get(windows[i].getStationId());
                    windowMapping[i] = sIndex;
                    possibleStations.add(sIndex);
                }
                int[] possibleStationsArray = possibleStations.stream().mapToInt(Integer::intValue).toArray();
                IntVar stationAssigned = model.intVar("station_" + file.getId(), possibleStationsArray);
                stationAssignedVars.put(file.getId(), stationAssigned);
                // contrainte élémentaire : stationAssigned = windowMapping[windowChoice]
                model.element(stationAssigned, windowMapping, windowChoice).post();

                // contraintes sur la fenêtre choisie :
                // si la fenêtre i est sélectionnée, alors start et end doivent être dans l'intervalle [window.start, window.end]
                for (int i = 0; i < windows.length; i++) {
                    TransmissionWindow tw = windows[i];
                    int winStart = toMinutes(tw.getStart());
                    int winEnd = toMinutes(tw.getEnd());
                    model.ifThen(
                        model.arithm(windowChoice, "=", i),
                        model.and(
                            model.arithm(start, ">=", winStart),
                            model.arithm(end, "<=", winEnd)
                        )
                    );
                }
            }
            satStartMap.put(satellite.getId(), satStarts);
            satDurationsMap.put(satellite.getId(), satDurations);
        }

        // contraintes de précédence : un fichier ne peut démarrer qu'après la fin de chacun de ses prédécesseurs
        for (Satellite satellite : instance.getSatellites()) {
            for (data.satellite.File file : satellite.getFiles()) {
                String[] preds = file.getPredecessors();
                if (preds != null) {
                    for (String predId : preds) {
                        if (endVars.containsKey(predId)) {
                            model.arithm(startVars.get(file.getId()), ">=", endVars.get(predId)).post();
                        } else {
                            System.err.println("Prédécesseur non trouvé pour " + file.getId() + " : " + predId);
                        }
                    }
                }
            }
        }

        // contrainte : si deux transmissions d'un même satellite se chevauchent,
        // elles doivent utiliser la même fenêtre (donc la même station)
        for (Satellite satellite : instance.getSatellites()) {
            data.satellite.File[] files = satellite.getFiles();
            for (int i = 0; i < files.length; i++) {
                for (int j = i + 1; j < files.length; j++) {
                    IntVar start1 = startVars.get(files[i].getId());
                    IntVar end1 = endVars.get(files[i].getId());
                    IntVar start2 = startVars.get(files[j].getId());
                    IntVar end2 = endVars.get(files[j].getId());
                    IntVar windowChoice1 = windowChoiceVars.get(files[i].getId());
                    IntVar windowChoice2 = windowChoiceVars.get(files[j].getId());
                    Constraint noOverlap = model.or(
                        model.arithm(end1, "<=", start2),
                        model.arithm(end2, "<=", start1)
                    );
                    model.ifThen(model.not(noOverlap), model.arithm(windowChoice1, "=", windowChoice2));
                }
            }
        }

        // contrainte cumulative pour la capacité des satellites
        for (Satellite satellite : instance.getSatellites()) {
            List<IntVar> startsList = satStartMap.get(satellite.getId());
            List<Integer> durationsList = satDurationsMap.get(satellite.getId());
            if (!startsList.isEmpty()) {
                IntVar[] startsArr = startsList.toArray(new IntVar[0]);
                int[] durationsArr = durationsList.stream().mapToInt(Integer::intValue).toArray();
                int[] heights = new int[durationsArr.length];
                Arrays.fill(heights, 1);
                model.cumulative(startsArr, durationsArr, heights, satellite.getNbTransmissionChannels());
            }
        }

        // contrainte de capacité des stations (modélisée par discrétisation temporelle avec un pas)
        for (int s = 0; s < stations.length; s++) {
            int stationCapacity = stations[s].getNbChannels();
            for (int t = 0; t <= horizon; t += timeStep) {
                List<BoolVar> indicators = new ArrayList<>();
                for (Satellite satellite : instance.getSatellites()) {
                    for (data.satellite.File file : satellite.getFiles()) {
                        IntVar start = startVars.get(file.getId());
                        IntVar end = endVars.get(file.getId());
                        IntVar stationAssigned = stationAssignedVars.get(file.getId());
                        BoolVar cond1 = model.boolVar();
                        model.arithm(start, "<=", t).reifyWith(cond1);
                        BoolVar cond2 = model.boolVar();
                        model.arithm(end, ">", t).reifyWith(cond2);
                        BoolVar cond3 = model.boolVar();
                        model.arithm(stationAssigned, "=", s).reifyWith(cond3);
                        BoolVar active = model.boolVar();
                        model.and(cond1, cond2, cond3).reifyWith(active);
                        indicators.add(active);
                    }
                }
                if (!indicators.isEmpty()) {
                    model.sum(indicators.toArray(new BoolVar[0]), "<=", stationCapacity).post();
                }
            }
        }

        // définition de la fonction objective : minimiser la durée totale du planning (makespan)
        Collection<IntVar> allEnds = endVars.values();
        int maxDuration = Collections.max(durationMap.values());
        IntVar makespan = model.intVar("makespan", 0, horizon + maxDuration);
        model.max(makespan, allEnds.toArray(new IntVar[0])).post();
        model.setObjective(Model.MINIMIZE, makespan);

        // recherche de la solution optimale
        Solver solver = model.getSolver();
        Solution solution = solver.findOptimalSolution(makespan, true);
        if (solution != null) {
            System.out.println("Solution optimale trouvée en " + (System.currentTimeMillis() - startTimeMillis) + " ms");
            System.out.println("Durée totale du planning (makespan) : " + solution.getIntVal(makespan) + " minutes");
            for (Satellite satellite : instance.getSatellites()) {
                for (data.satellite.File file : satellite.getFiles()) {
                    IntVar start = startVars.get(file.getId());
                    IntVar end = endVars.get(file.getId());
                    IntVar windowChoice = windowChoiceVars.get(file.getId());
                    IntVar stationAssigned = stationAssignedVars.get(file.getId());
                    TransmissionWindow[] windows = satellite.getTransmissionWindows();
                    int chosenWindowIndex = solution.getIntVal(windowChoice);
                    TransmissionWindow chosenWindow = windows[chosenWindowIndex];
                    String stationName = "";
                    for (Station st : stations) {
                        if (stationIndex.get(st.getId()) == solution.getIntVal(stationAssigned)) {
                            stationName = st.getId();
                            break;
                        }
                    }
                    System.out.println("Fichier " + file.getId() + " : démarrage à " + solution.getIntVal(start) +
                        " minutes, fin à " + solution.getIntVal(end) +
                        " minutes, via la fenêtre (" + chosenWindow.getStationId() + " [" + stationName + "] : " +
                        toTimeString(chosenWindow.getStart()) + " - " + toTimeString(chosenWindow.getEnd()) + ")");
                }
            }
        } else {
            System.out.println("Aucune solution trouvée.");
        }
    }

    
    // convertit un LocalDateTime en minutes écoulées depuis BASE_TIME.
    private static int toMinutes(LocalDateTime dt) {
        return (int) ChronoUnit.MINUTES.between(BASE_TIME, dt);
    }

    // formate un LocalDateTime au format HH:mm.
    private static String toTimeString(LocalDateTime dt) {
        return String.format("%02d:%02d", dt.getHour(), dt.getMinute());
    }
}

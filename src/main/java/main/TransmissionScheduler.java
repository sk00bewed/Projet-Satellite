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
        TransmissionInstance instance = Utils.fromFile("data/transmission/transmission_c.json", TransmissionInstance.class);
        if (instance == null) {
            System.err.println("Erreur lors de la lecture du fichier JSON.");
            return;
        }

        Model model = new Model("Planification Téléchargement Satellite");

        // Maps pour stocker les variables associées à chaque fichier traité
        Map<String, IntVar> startVars = new HashMap<>();
        Map<String, IntVar> endVars = new HashMap<>();
        Map<String, IntVar> windowChoiceVars = new HashMap<>();
        Map<String, IntVar> stationAssignedVars = new HashMap<>();
        // Stockage des durées calculées pour chaque fichier traité
        Map<String, Integer> durationMap = new HashMap<>();

        // Table de correspondance stationId -> indice (pour la capacité des stations)
        Station[] stations = instance.getStations();
        Map<String, Integer> stationIndex = new HashMap<>();
        for (int i = 0; i < stations.length; i++) {
            stationIndex.put(stations[i].getId(), i);
        }

        // Pour la contrainte cumulative sur les satellites, on regroupe par satellite
        Map<String, List<IntVar>> satStartMap = new HashMap<>();
        Map<String, List<Integer>> satDurationsMap = new HashMap<>();

        // Calcul de l'horizon temporel : fin maximale d'une fenêtre sur tous les satellites
        int horizon = 0;
        for (Satellite sat : instance.getSatellites()) {
            for (TransmissionWindow tw : sat.getTransmissionWindows()) {
                int end = toMinutes(tw.getEnd());
                if (end > horizon) {
                    horizon = end;
                }
            }
        }

        // Pour la capacité des stations, on utilisera une discrétisation avec un pas (ici 5 minutes)
        int timeStep = 5;
        // Construction d'un ensemble de points temporels pertinents
        Set<Integer> relevantTimes = new TreeSet<>();
        for (Satellite sat : instance.getSatellites()) {
            for (TransmissionWindow tw : sat.getTransmissionWindows()) {
                relevantTimes.add(toMinutes(tw.getStart()));
                relevantTimes.add(toMinutes(tw.getEnd()));
            }
            for (data.satellite.File file : sat.getFiles()) {
                int releaseTime = toMinutes(file.getReleaseDateTime());
                relevantTimes.add(releaseTime);
                relevantTimes.add(Math.max(horizon, releaseTime));
            }
        }
        List<Integer> filteredTimes = new ArrayList<>();
        int lastAdded = -timeStep;
        for (Integer t : relevantTimes) {
            if (t - lastAdded >= timeStep) {
                filteredTimes.add(t);
                lastAdded = t;
            }
        }

        // Itération sur chaque satellite et sur chaque fichier
        for (Satellite satellite : instance.getSatellites()) {
            List<IntVar> satStarts = new ArrayList<>();
            List<Integer> satDurations = new ArrayList<>();

            TransmissionWindow[] windows = satellite.getTransmissionWindows();
            for (data.satellite.File file : satellite.getFiles()) {
                int releaseTime = toMinutes(file.getReleaseDateTime());
                int duration = (int) Math.ceil(file.getSize() / EFFECTIVE_RATE);
                durationMap.put(file.getId(), duration);

                int fileStartUpperBound = Math.max(horizon, releaseTime);
                IntVar start = model.intVar("start_" + file.getId(), releaseTime, fileStartUpperBound);
                IntVar end = model.intVar("end_" + file.getId(), releaseTime + duration, fileStartUpperBound + duration);
                model.arithm(end, "=", start, "+", duration).post();

                // Réduction du domaine de windowChoice en filtrant les fenêtres viables
                List<Integer> viableWindowIndices = new ArrayList<>();
                for (int i = 0; i < windows.length; i++) {
                    int winStart = toMinutes(windows[i].getStart());
                    int winEnd = toMinutes(windows[i].getEnd());
                    if (Math.max(releaseTime, winStart) + duration <= winEnd) {
                        viableWindowIndices.add(i);
                    }
                }
                if (viableWindowIndices.isEmpty()) {
                    System.err.println("Aucune fenêtre viable pour le fichier " + file.getId());
                    // On ne traite pas ce fichier dans le modèle
                    continue;
                }
                int[] viableWindowIndicesArray = viableWindowIndices.stream().mapToInt(Integer::intValue).toArray();
                IntVar windowChoice = model.intVar("window_" + file.getId(), viableWindowIndicesArray);
                // Association des variables créées pour ce fichier
                startVars.put(file.getId(), start);
                endVars.put(file.getId(), end);
                windowChoiceVars.put(file.getId(), windowChoice);
                satStarts.add(start);
                satDurations.add(duration);

                // Association de la fenêtre à une station via un tableau de correspondance
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
                model.element(stationAssigned, windowMapping, windowChoice).post();

                // Contraintes sur la fenêtre choisie
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

        // Contraintes de précédence (on ne traite que les fichiers ayant été ajoutés)
        for (Satellite satellite : instance.getSatellites()) {
            for (data.satellite.File file : satellite.getFiles()) {
                if (!startVars.containsKey(file.getId()))
                    continue;
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

        // Contrainte de chevauchement sur un même satellite
        for (Satellite satellite : instance.getSatellites()) {
            for (data.satellite.File file : satellite.getFiles()) {
                if (!startVars.containsKey(file.getId()))
                    continue;
            }
            data.satellite.File[] files = satellite.getFiles();
            for (int i = 0; i < files.length; i++) {
                if (!startVars.containsKey(files[i].getId()))
                    continue;
                for (int j = i + 1; j < files.length; j++) {
                    if (!startVars.containsKey(files[j].getId()))
                        continue;
                    IntVar start1 = startVars.get(files[i].getId());
                    IntVar end1 = endVars.get(files[i].getId());
                    IntVar start2 = startVars.get(files[j].getId());
                    IntVar end2 = endVars.get(files[j].getId());
                    IntVar windowChoice1 = windowChoiceVars.get(files[i].getId());
                    IntVar windowChoice2 = windowChoiceVars.get(files[j].getId());
                    
                    BoolVar overlap = model.boolVar("overlap_" + files[i].getId() + "_" + files[j].getId());
                    model.and(
                        model.arithm(start1, "<", end2),
                        model.arithm(start2, "<", end1)
                    ).reifyWith(overlap);
                    
                    model.ifThen(
                        model.arithm(overlap, "=", 1),
                        model.arithm(windowChoice1, "=", windowChoice2)
                    );
                }
            }
        }

        // Contrainte cumulative pour la capacité des satellites
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

        // Contrainte de capacité des stations (en utilisant filteredTimes)
        for (int s = 0; s < stations.length; s++) {
            int stationCapacity = stations[s].getNbChannels();
            for (Integer t : filteredTimes) {
                List<BoolVar> indicators = new ArrayList<>();
                for (Satellite satellite : instance.getSatellites()) {
                    for (data.satellite.File file : satellite.getFiles()) {
                        if (!startVars.containsKey(file.getId()))
                            continue;
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

        // Fonction objective : minimiser le makespan (maximum de tous les temps de fin)
        Collection<IntVar> allEnds = endVars.values();
        int maxDuration = Collections.max(durationMap.values());
        IntVar makespan = model.intVar("makespan", 0, horizon + maxDuration);
        model.max(makespan, allEnds.toArray(new IntVar[0])).post();
        model.setObjective(Model.MINIMIZE, makespan);

        // Limitation du temps de résolution
        Solver solver = model.getSolver();
        solver.limitTime("60s");

        // Recherche de la solution optimale
        Solution solution = solver.findOptimalSolution(makespan, true);
        if (solution != null) {
            System.out.println("Solution optimale trouvée en " + (System.currentTimeMillis() - startTimeMillis) + " ms");
            System.out.println("Durée totale du planning (makespan) : " + solution.getIntVal(makespan) + " minutes");
            for (Satellite satellite : instance.getSatellites()) {
                for (data.satellite.File file : satellite.getFiles()) {
                    if (!startVars.containsKey(file.getId()))
                        continue;
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

    // Convertit un LocalDateTime en minutes écoulées depuis BASE_TIME.
    private static int toMinutes(LocalDateTime dt) {
        return (int) ChronoUnit.MINUTES.between(BASE_TIME, dt);
    }

    // Formate un LocalDateTime au format HH:mm.
    private static String toTimeString(LocalDateTime dt) {
        return String.format("%02d:%02d", dt.getHour(), dt.getMinute());
    }
}

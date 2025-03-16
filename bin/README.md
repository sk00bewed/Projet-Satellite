# Planification téléchargement satellite
Des satellites d'observation de la Terre font des acquisitions de photos à certains horaires (release date). Ces photos doivent ensuite être téléchargées vers des antennes au sol. Certaines photos en nécessitent d'autres pour leur recomposition, ce qui nécessite de télécharger ces photos "précédentes" d'abord.
Les antennes au sol possèdent un identifiant et ont également une capacité de téléchargement exprimée en Mo/min.
Un satellite est déterminé par son identifiant, et contient des fichiers de taille différente (exprimée en Mo). Chaque satellite possède un flux maximal de téléchargement, exprimé en Mo/min.
Les satellites ne peuvent être reliés qu'à certaines antennes au sol, et durant des fenêtres de temps précises (qui se répétent toutes les 24h dans notre cas). Il est à noter qu'un satellite ne peut être relié qu'à une seule antenne au sol à la fois, tandis qu'une antenne au sol n'a pas de limites de satellites auxquels elle peut être reliée.
La durée de téléchargement d'un fichier entre un satellite et une antenne est variable et vaut la taille du fichire lorsqu'elle est multipliée au taux d'occupation du flux.

Vous devez établir un planning de téléchargement de l'ensemble des fichiers depuis les satellites vers les antennes au sol en respectant les contraintes citées. Vous devez minimiser la durée totale de ce planning.

Contraintes du problème :
- Précédence : certains fichiers ne peuvent être téléchargés que lorsque tous leurs prédecesseurs ont été intégralement téléchargés
- Heure de prise de photo : un fichier ne peut être téléchargé qu'une fois la photo prise (release date)
- Capacité de téléchargement des satellites
- Capacité de téléchargement des antennes au sol
- Un satellite ne peut être connecté qu'à une seule antenne au sol à la fois

# La Bulle de Kiria 🫧📚

App Android de suivi de lecture : livres en cours / à lire / lus, progression quotidienne, sessions chronométrées, fiches livres (recherche Google Books + Open Library), notes, et statistiques.

## Obtenir l'APK (sans rien installer)
1. Crée un dépôt GitHub et envoie-y tout le contenu de ce dossier (y compris `.github/`).
2. Onglet **Actions** → le workflow « Construire l'APK » se lance tout seul (ou bouton *Run workflow*).
3. À la fin (~3 min), télécharge l'artefact **LaBulleDeKiria-apk**, dézippe-le, envoie `LaBulleDeKiria.apk` sur ton téléphone et installe-le (autoriser les « sources inconnues »).

## Avec Android Studio
Ouvre le dossier, attends la synchro Gradle, puis *Run ▶* sur ton téléphone.

## Structure
- `app/src/main/assets/index.html` : toute l'interface et la logique (données stockées sur le téléphone).
- `app/src/main/java/fr/kiria/bulle/MainActivity.java` : coque Android (WebView, bouton retour, partage de sauvegarde, écran allumé pendant les sessions).

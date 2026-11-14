<div align="center">

# StreamLab

**Radio du monde entier, tes chaînes TV et tes vidéos — sur ton téléphone, ta TV et dans la voiture.**

Application Android native (Kotlin · Jetpack Compose · Media3), sans compte, sans publicité, sans pistage.

![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white)
![Media3](https://img.shields.io/badge/Lecteur-Media3%20ExoPlayer-1a73e8)
![Android Auto](https://img.shields.io/badge/Android%20Auto-compatible-34A853)
![Langues](https://img.shields.io/badge/Langues-FR%20%C2%B7%20EN%20%C2%B7%20AR-555)
![Licence](https://img.shields.io/badge/Licence-Tous%20droits%20r%C3%A9serv%C3%A9s-critical)

<a href="#installer">Installer</a> ·
<a href="#ce-que-fait-streamlab-de-a-%C3%A0-z">Fonctions</a> ·
<a href="#android-auto">Android Auto</a> ·
<a href="#confidentialit%C3%A9">Confidentialité</a> ·
<a href="#english-summary">English</a>

<br>

<img src="docs/screenshots/01-accueil.png" width="200" alt="Accueil">
<img src="docs/screenshots/06-radio-france.png" width="200" alt="Radios de France">
<img src="docs/screenshots/03-tv.png" width="200" alt="Chaînes TV">
<img src="docs/screenshots/08-reglages.png" width="200" alt="Réglages">

</div>

---

## Pourquoi StreamLab ?

La plupart des lecteurs IPTV ou radio sont soit des usines à gaz, soit truffés de publicités et de traqueurs.
StreamLab fait le choix inverse : **une seule appli claire** pour écouter la radio de n'importe quel pays,
regarder **tes propres** chaînes et vidéos, et retrouver tout ça **dans la voiture** — sans créer de compte
et sans que rien ne quitte ton téléphone.

> StreamLab **ne fournit aucune chaîne ni aucun contenu**. Tu ajoutes tes propres sources (listes M3U,
> fournisseur Xtream, guide XMLTV, disque réseau) et tu restes responsable d'en avoir le droit.
> Les radios proviennent de l'annuaire public et ouvert [Radio Browser](https://www.radio-browser.info).

---

## Deux éditions

| | **StreamLab** (Google Play) | **StreamLab Passagers** (édition personnelle) |
|---|---|---|
| Toute l'appli : radio, TV, multivue, vidéos, disque réseau, Cast, favoris, réveil… | ✅ | ✅ identique |
| Android Auto — radio et son des chaînes (écran multimédia) | ✅ | ✅ |
| S'installe seule (aucune autre appli requise) | ✅ | ✅ |
| Android Auto — **vidéo à l'arrêt** (voiture garée) | — | ✅ |
| En roulant | audio uniquement | Android Auto ferme l'image, **le son continue** |
| De nouveau à l'arrêt | — | **la même chaîne revient en plein écran** |
| Écran de la voiture maintenu allumé (option) | — | ✅ |
| Distribution | Google Play — *bientôt* | non distribuée |

La vidéo sur l'écran de la voiture s'appuie sur la catégorie officielle **« applis à l'arrêt »** d'Android Auto
(Android 15 et plus) : c'est Android Auto lui-même qui coupe l'image dès que la voiture roule.
Aucune règle de sécurité de conduite n'est contournée.

> 📲 **Google Play** : la version publique arrive bientôt. Le lien sera ajouté ici.

---

## Ce que fait StreamLab, de A à Z

### 📻 Radio
- **Toutes les radios du monde, rangées par pays** — plus de 40 000 stations (annuaire Radio Browser), avec drapeaux et nombre de stations.
- **« Près de vous »** : ton pays est proposé en premier (détecté sur l'appareil, **sans GPS ni permission de localisation**).
- **« Les plus aimées »** et **« Tendances »** dans chaque pays, recherche instantanée.
- **Noms propres** : « RADIO-XYZ 128k AAC (geo-blocked) » devient « Radio XYZ » ; doublons fusionnés ; genres lisibles.
- **Titre en cours** affiché (artiste – titre) quand la radio l'envoie.
- **Favoris en une touche** (étoile), et **« Écouter mes radios en continu »** : tes favorites s'enchaînent comme une playlist.
- **Sources de secours** : si une radio tombe, StreamLab essaie automatiquement jusqu'à **3 adresses** avant de passer à la suivante.
- **Pas de publicité en boucle** : le lecteur ne recoupe plus les longs flux (la pub d'ouverture ne revient plus toutes les 30 s).
- **Radio-réveil** : réveille-toi avec ta station préférée.
- **Commande vocale** : « Ok Google, mets RTL » trouve la meilleure correspondance, même si elle n'est pas dans tes favoris.

<p align="center">
<img src="docs/screenshots/02-radio-pays.png" width="220" alt="Radios par pays">
<img src="docs/screenshots/07-radio-lecture.png" width="220" alt="Lecture d'une radio">
</p>

### 📺 TV
- **Tes chaînes, rangées par pays et par langue** — le classement lit le flux de diffusion (« @FR ») avant le nom du propriétaire, et ne confond pas « SD/HD » avec un pays.
- **Lecteur plein écran** avec bandeau « en direct », **programme en cours / à suivre** (guide XMLTV), zapping au doigt et liste des chaînes.
- **Vérification des chaînes** : un bouton teste les flux et signale ceux qui ne répondent pas.
- **Multivue** : **2 à 4 chaînes en même temps**, en mosaïque, le son sur celle que tu choisis.
- **Image dans l'image (PiP)** : la TV continue dans une petite fenêtre quand tu quittes l'appli.
- **Vitesse de lecture** (0,75× à 2×) pour les vidéos.
- **Ajouter ses chaînes** : liste M3U (adresse ou fichier), fournisseur Xtream Codes, chaîne unique, guide XMLTV.
- **Sources de secours** par chaîne, comme pour la radio.

<p align="center">
<img src="docs/screenshots/04-tv-plein-ecran.png" width="220" alt="TV en plein écran">
<img src="docs/screenshots/05-multivue.png" width="220" alt="Multivue">
<img src="docs/screenshots/09-ajouter-chaines.png" width="220" alt="Ajouter mes chaînes">
</p>

### 🎬 Vidéos et disque réseau
- **Disque réseau à la maison (WebDAV)** : Synology, QNAP, Nextcloud, box… Parcours tes dossiers et lance un film **sans le copier** sur le téléphone.
- **Fichiers du téléphone** et **« Continuer à regarder »** à l'endroit exact où tu t'étais arrêté.
- **Ouvrir dans un autre lecteur** (VLC, MX Player…) en un geste.

### 📡 Sur la TV du salon
- **Google Cast** : envoie la radio, une chaîne ou une vidéo du téléphone vers ta TV / Chromecast.
- Les vidéos stockées sur le téléphone sont servies localement, protégées par un jeton à usage unique.

### 🚗 Android Auto
- Arborescence claire au volant : **Radio par pays, Favoris, Récents, TV, Sources**.
- **Station suivante / précédente** depuis le volant, commande vocale, pochettes et logos.
- Messages d'erreur courts et lisibles (jamais de codes techniques à l'écran).
- Édition Passagers : vidéo à l'arrêt, son seul en roulant, retour automatique en plein écran à l'arrêt (voir plus bas).

### 🧭 Pratique
- **Favoris** et **Récents** partout.
- **Partager avec un ami** : tes radios favorites et tes listes, par **QR code** (jamais d'identifiants dans le code).
- **Mode enfants** protégé par un code PIN.
- **Économie de données** : en 4G/5G, la vidéo passe en 480p (environ 3 fois moins de données) ; qualité maximale en Wi-Fi.
- **Mode hors ligne** clair : un message simple au lieu d'un écran d'erreur.
- **Conseils au premier lancement**, discrets, une seule fois.
- **Services web** : raccourcis vers les applis officielles (Netflix, YouTube, Prime Video, Disney+, Twitch, Plex, Jellyfin, Emby). StreamLab **n'intègre ni ne contourne** leur lecteur ni leurs protections.

### 🎨 Interface
- Thème **Cinéma** sombre, **clair** ou **système** ; liste compacte en option.
- Icônes vectorielles nettes, logos des stations, monogrammes élégants quand une station n'a pas de logo.
- **Trois langues complètes** : français, anglais, **arabe (de droite à gauche)** — ou la langue du système.
- Accessibilité : descriptions pour les lecteurs d'écran, grandes zones tactiles.

---

## Android Auto

### Édition Google Play
Rien à faire : branche ton téléphone, StreamLab apparaît dans les applis multimédias d'Android Auto.

### Édition Passagers (vidéo à l'arrêt)
Prérequis : **Android 15 ou plus** et un écran de voiture tactile.

1. Installe l'APK `parkedDebug` (nom affiché : **StreamLab Passagers**). C'est l'appli complète : pas besoin d'installer aussi la version Play.
2. Sur le téléphone : **Android Auto → Paramètres** → touche 10 fois **« Version »** pour activer le mode développeur.
3. Menu ⋮ → **Paramètres pour les développeurs** → coche **« Sources inconnues »**.
4. Rebranche le téléphone : l'appli apparaît dans le lanceur de la voiture.

**À l'arrêt** la chaîne s'affiche en plein écran ; **en roulant** Android Auto ferme l'image et **le son continue**
(avec les commandes sur l'écran multimédia et au volant) ; **de nouveau à l'arrêt**, la même chaîne revient en plein écran.
Dans les réglages de l'écran voiture, l'option **« Garder l'écran allumé »** évite la mise en veille pendant la vidéo.

---

## Installer

**Google Play** — *bientôt*.

**Depuis les sources** (JDK 17, Android SDK 36) :

```bash
./gradlew :app:assembleDebug          # édition standard
./gradlew :app:assembleParkedDebug    # édition Passagers (vidéo à l'arrêt)
```

Aucune clé ni aucun secret n'est présent dans ce dépôt : la signature de publication se lit uniquement
depuis des variables d'environnement.

---

## Qualité

- **Architecture** : Kotlin, Jetpack Compose, Media3 (ExoPlayer, `MediaLibraryService`, `MediaController`), Room, OkHttp.
- **Tests** : 131 tests unitaires et 90 tests instrumentés (navigation, lecture, Android Auto, Cast, multivue, disque réseau, radio, failover…).
- **Robustesse** : plus de 75 000 actions aléatoires (stress test *monkey*) sur les versions debug et release, sans plantage.
- **Version publiée** : code réduit et obfusqué (R8), lint sans erreur sur toutes les variantes.
- **Sécurité** :
  - sources et identifiants **chiffrés sur l'appareil** (Android Keystore, AES-GCM) ;
  - réseau en **HTTPS** ; les adresses du réseau local sont refusées sauf le disque réseau que tu as ajouté ;
  - réponses WebDAV durcies (DTD/entités refusées) ;
  - aucun identifiant dans les journaux ;
  - sauvegarde Android des données désactivée.

---

## Confidentialité

- **Aucun compte**, **aucune publicité**, **aucun outil de mesure d'audience**.
- Tes listes, favoris, historique et réglages **restent sur ton téléphone**.
- Connexions réseau uniquement vers : les sources que tu ajoutes, l'annuaire Radio Browser, Google Cast et le scanner QR de Google Play services (seulement quand tu les utilises).

Politique complète : [docs/play/POLITIQUE_CONFIDENTIALITE.md](docs/play/POLITIQUE_CONFIDENTIALITE.md).

---

## Licence et propriété

© 2026 Younes Ajbilou — **Tous droits réservés.**

Ce dépôt est public **pour consultation uniquement**. Aucune licence open source n'est accordée :
la copie, la modification, la redistribution, la publication sur une boutique d'applications ou la réutilisation
du code, du nom **StreamLab**, de l'icône, de l'interface ou des captures sont **interdites sans accord écrit**.
Voir [LICENSE](LICENSE) et [NOTICE](NOTICE.md).

Les noms et logos de services tiers (Netflix, YouTube, Prime Video, Disney+, Android Auto, Google Cast…)
appartiennent à leurs propriétaires respectifs. StreamLab n'est affilié à aucun d'eux.

---

## English summary

**StreamLab** is a native Android app (Kotlin, Jetpack Compose, Media3) for **world radio, your own TV channels and videos** — on the phone, on the TV (Google Cast) and **in the car (Android Auto)**. No account, no ads, no tracking.

- 40,000+ radio stations by country (Radio Browser), "near you", top/trending, clean names, song on air, favourites playlist, automatic backup sources (up to 3), radio alarm, voice search ("Ok Google, play …").
- Your own TV channels (M3U, Xtream Codes, XMLTV), sorted by country and language, full-screen player with now/next, channel health check, **multiview (2–4 channels)**, picture-in-picture, playback speed.
- Home network drive (WebDAV) without copying files, continue watching, external player, Google Cast, QR sharing, kids mode with PIN, data saver on mobile data, offline mode.
- French, English and Arabic (RTL). Dark, light or system theme.
- **Two editions**: the Google Play edition (audio in Android Auto) and a personal "Passengers" edition that uses Android Auto's official *parked apps* category: video while parked, audio only while driving, back to full screen when parked again.
- StreamLab provides **no content**: users add their own lawful sources.

**All rights reserved.** This repository is public for viewing only; no open-source licence is granted. Google Play link: *coming soon*.

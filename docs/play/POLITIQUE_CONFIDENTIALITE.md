# Politique de confidentialité — StreamLab

*Dernière mise à jour : [DATE DE PUBLICATION]*
*Éditeur : Younes AJBILOU — Contact : streamlab.contact@gmail.com*

> À compléter avant publication : la date. Cette page doit être publiée à une adresse web
> publique (par exemple une page GitHub Pages ou Google Sites gratuite) et cette adresse doit être
> renseignée dans la Play Console et dans l'appli (Réglages → À propos).

## En bref

- StreamLab **ne demande aucun compte**, **n'affiche aucune publicité** et **ne contient aucun outil de mesure d'audience ou de suivi**.
- Vos playlists, favoris, historique et réglages **restent sur votre téléphone**. L'éditeur n'y a pas accès.
- StreamLab **ne fournit aucune chaîne ni aucun contenu**. Vous ajoutez vous-même vos sources et vous êtes responsable de disposer des droits nécessaires.

## 1. Données conservées sur votre appareil uniquement

| Donnée | Pourquoi | Protection |
|---|---|---|
| Adresses de playlists M3U, de guides TV (XMLTV), de chaînes | Lire les sources que vous avez ajoutées | Chiffrées sur l'appareil (Android Keystore, AES-GCM) |
| Identifiants Xtream Codes que vous saisissez | Se connecter au service que vous avez choisi | Chiffrés sur l'appareil (Android Keystore, AES-GCM) |
| Favoris, chaînes récentes, position de lecture | Reprendre où vous en étiez | Base locale de l'appli |
| Langue, thème, réglages | Personnaliser l'appli | Préférences locales |
| Adresse de votre serveur Plex/Jellyfin (si vous la saisissez) | Ouvrir votre serveur | Fichier local de l'appli |
| Adresse et identifiants de votre disque réseau (WebDAV), si vous en ajoutez un | Parcourir et lire vos vidéos sans les copier | Chiffrés sur l'appareil (Android Keystore, AES-GCM) ; seule cette adresse locale est autorisée |

La sauvegarde automatique Android de ces données est **désactivée**. Désinstaller l'appli ou utiliser « Effacer les données » supprime tout.

## 2. Connexions réseau effectuées par l'appli

StreamLab se connecte **uniquement** :

1. **Aux adresses que vous avez ajoutées** (playlists, guides TV, flux audio/vidéo, logos). Comme pour tout accès à Internet, ces serveurs voient l'adresse IP de votre connexion. Leurs pratiques relèvent de leurs propres politiques.
2. **À l'annuaire public de radios Radio Browser** (radio-browser.info) pour afficher les radios par pays. L'appli envoie le code du pays ou le terme recherché. Aucun identifiant personnel n'est envoyé.
3. **Au scanner de QR code de Google Play services**, uniquement quand vous appuyez sur « Scanner le code d'un ami ». L'image est analysée par Google Play services ; StreamLab ne reçoit que le texte du code et n'a pas accès à la caméra (aucune permission caméra demandée). Le code de partage ne contient que des noms et des liens publics, jamais d'identifiants.
4. **À Google Cast**, uniquement si vous choisissez de diffuser vers un appareil Chromecast. Le SDK Google Cast (services Google Play) est alors utilisé selon la politique de Google : https://policies.google.com/privacy

## 3. Pays détecté pour « Près de vous »

Pour proposer les radios du pays où vous vous trouvez, l'appli lit **sur l'appareil** le pays du réseau mobile, de la carte SIM ou de la région du téléphone. **Aucune localisation GPS n'est utilisée et aucune permission de localisation n'est demandée.** Cette information n'est ni enregistrée ni transmise.

## 4. Services tiers (Netflix, YouTube, Prime Video, etc.)

Les boutons de services **ouvrent l'application officielle de l'éditeur** si elle est installée, sinon sa page sur Google Play. StreamLab ne lit pas, ne stocke pas et ne transmet pas vos identifiants, cookies ou contenus de ces services.

## 5. Permissions Android

| Permission | Usage |
|---|---|
| Internet, état du réseau | Lire vos sources et l'annuaire radio |
| Notifications | Contrôles de lecture en arrière-plan |
| Service au premier plan « lecture multimédia » | Continuer la radio écran éteint et dans Android Auto |
| Démarrage / verrou de mise en veille | Mises à jour planifiées de vos playlists (WorkManager), lecture continue, et reprogrammation du radio-réveil après un redémarrage |
| Alarmes et rappels (accordée par vous dans les réglages Android) | Faire sonner le radio-réveil à l'heure exacte ; utilisée uniquement si vous activez le radio-réveil |

Le **contrôle parental** (mode enfants) enregistre seulement une empreinte chiffrée irréversible du code parent, sur l'appareil. Le code lui-même n'est jamais stocké ni transmis.

## 6. Enfants

StreamLab ne s'adresse pas spécifiquement aux enfants et ne collecte sciemment aucune donnée les concernant.

## 7. Vos droits

Aucune donnée personnelle n'étant transmise à l'éditeur, celui-ci n'en détient aucune. Pour toute question : streamlab.contact@gmail.com. Vous pouvez saisir la CNIL (www.cnil.fr).

## 8. Modifications

Toute modification de cette politique sera publiée sur cette page avec une nouvelle date.

---

# Privacy policy — StreamLab (English)

*Last updated: [PUBLICATION DATE] — Publisher: Younes AJBILOU — Contact: streamlab.contact@gmail.com*

- StreamLab requires **no account**, shows **no ads** and contains **no analytics or tracking**.
- Your playlists, favourites, history and settings **stay on your device**; the publisher has no access to them. Playlist/guide/channel addresses and Xtream credentials are **encrypted on the device** (Android Keystore, AES-GCM). Android auto-backup is disabled.
- StreamLab **provides no channels or content**. You add your own sources and are responsible for holding the necessary rights.
- Network connections go only to: (1) the addresses you added (those servers see your IP address); (2) the public Radio Browser directory (radio-browser.info), receiving only a country code or search term; (3) Google Cast, only when you choose to cast (Google's privacy policy applies).
- "Near you" radios use the mobile network / SIM / device region country **on the device**. No GPS, no location permission; nothing is stored or sent.
- The radio alarm uses Android's "Alarms & reminders" permission, which you grant in Android settings, only to ring on time. The parental control stores only an irreversible hash of the parent code, on the device.
- Service buttons (Netflix, YouTube, Prime Video…) open the publisher's **official app** or its Google Play page. StreamLab never reads or stores their credentials or content.
- Questions: streamlab.contact@gmail.com.

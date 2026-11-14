package app.cleanroom.streamlab.player

internal object ControllerPolicy {
    fun canPlaySource(controllerPackage: String, controllerUid: Int, appPackage: String, appUid: Int): Boolean =
        controllerPackage == appPackage && controllerUid == appUid

    fun isTrustedPassengerCompanion(
        controllerPackage: String,
        appPackage: String,
        signaturesMatch: Boolean,
    ): Boolean = signaturesMatch && controllerPackage == "$appPackage.car"
}

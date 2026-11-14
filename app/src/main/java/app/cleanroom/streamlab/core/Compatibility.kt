package app.cleanroom.streamlab.core

enum class FeatureStatus { NOT_IMPLEMENTED }

interface StalkerPortalGateway { val status: FeatureStatus }
interface CastGateway { val status: FeatureStatus }
interface MultiViewGateway { val status: FeatureStatus }
interface CatchUpGateway { val status: FeatureStatus }
interface ProxyProfileGateway { val status: FeatureStatus }
interface PreviewCacheGateway { val status: FeatureStatus }
interface LegacyHttpGateway { val status: FeatureStatus }

object Compatibility {
    val stalker = FeatureStatus.NOT_IMPLEMENTED
    val cast = FeatureStatus.NOT_IMPLEMENTED
    val multiView = FeatureStatus.NOT_IMPLEMENTED
    val catchUp = FeatureStatus.NOT_IMPLEMENTED
    val proxy = FeatureStatus.NOT_IMPLEMENTED
    val previews = FeatureStatus.NOT_IMPLEMENTED
    val legacyHttp = FeatureStatus.NOT_IMPLEMENTED
}
package play.api.oauth

import _root_.oauth.signpost.{ OAuthConsumer, OAuthProvider }
import _root_.oauth.signpost.exception.OAuthException
import _root_.oauth.signpost.basic.{ DefaultOAuthConsumer, DefaultOAuthProvider }
import _root_.oauth.signpost.{ OAuthConsumer, AbstractOAuthConsumer }
import oauth._

import play.api.ws._
import play.api.WS.WSRequestBase

/**
 * Library to access ressources protected by OAuth 1.0a.
 *
 */
case class OAuth(info: ServiceInfo, use10a: Boolean = true) {

  private val provider = new DefaultOAuthProvider(info.requestTokenURL, info.accessTokenURL, info.authorizationURL)
  provider.setOAuth10a(use10a)

  /**
   * Request the request token and secret.
   * @param callbackURL the URL where the provider should redirect to (usually a URL on the current app)
   * @return A Right(RequestToken) in case of success, Left(OAuthException) otherwise
   */
  def retrieveRequestToken(callbackURL: String): Either[OAuthException, RequestToken] = {
    val consumer = new DefaultOAuthConsumer(info.key.key, info.key.secret)
    try {
      provider.retrieveRequestToken(consumer, callbackURL)
      Right(RequestToken(consumer.getToken(), consumer.getTokenSecret()))
    } catch {
      case e: OAuthException => Left(e)
    }
  }

  /**
   * Exchange a request token for an access token.
   * @param the token/secret pair obtained from a previous call
   * @param verifier a string you got through your user, with redirection
   * @return A Right(RequestToken) in case of success, Left(OAuthException) otherwise
   */
  def retrieveAccessToken(token: RequestToken, verifier: String): Either[OAuthException, RequestToken] = {
    val consumer = new DefaultOAuthConsumer(info.key.key, info.key.secret)
    consumer.setTokenWithSecret(token.token, token.secret)
    try {
      provider.retrieveAccessToken(consumer, verifier)
      Right(RequestToken(consumer.getToken(), consumer.getTokenSecret()))
    } catch {
      case e: OAuthException => Left(e)
    }
  }

  /**
   * The URL where the user needs to be redirected to grant authorization to your application
   * @param token request token
   */
  def redirectUrl(token: String): String =
    _root_.oauth.signpost.OAuth.addQueryParameters(provider.getAuthorizationWebsiteUrl(),
      _root_.oauth.signpost.OAuth.OAUTH_TOKEN, token);

}

/**
 * A consumer key / consumer secret pair that the OAuth provider gave you, to identify your application
 */
case class ConsumerKey(key: String, secret: String)

/**
 * A request token / token secret pair, to be used for a specific user
 */
case class RequestToken(token: String, secret: String)

/**
 * The information identifying a oauth provider: URLs and the consumer key / consumer secret pair
 */
case class ServiceInfo(requestTokenURL: String, accessTokenURL: String, authorizationURL: String, key: ConsumerKey)

/**
 * A signature calculator for the Play WS API. Example:
 * WS.url("http://example.com/protected").sign(OAuthCalculator(service, tokens)).get()
 */
case class OAuthCalculator(consumerKey: ConsumerKey, token: RequestToken) extends AbstractOAuthConsumer(consumerKey.key, consumerKey.secret) with SignatureCalculator {

  import _root_.oauth.signpost.http.HttpRequest

  this.setTokenWithSecret(token.token, token.secret)

  override protected def wrap(request: Any) = request match {
    case r: WSRequestBase[_, _] => new WSRequestAdapter(r)
    case _ => throw new IllegalArgumentException("OAuthCalculator expects requests of type play.api.WS.WSRequestBase")
  }

  override def sign(request: WSRequestBase[_, _]): Unit = sign(wrap(request))

  class WSRequestAdapter(request: WSRequestBase[_, _]) extends HttpRequest {

    import scala.collection.JavaConversions._

    override def unwrap() = request

    override def getAllHeaders(): java.util.Map[String, String] =
      request.allHeaders.map { entry => (entry._1, entry._2.headOption) }
        .filter { entry => entry._2.isDefined }
        .map { entry => (entry._1, entry._2.get) }

    override def getHeader(name: String): String = request.header(name).getOrElse("")

    override def getContentType(): String = getHeader("Content-Type")

    override def getMessagePayload() = null

    override def getMethod(): String = this.request.method

    override def setHeader(name: String, value: String) {
      request.setHeader(name, value)
    }

    override def getRequestUrl() = request.url

    override def setRequestUrl(url: String) {
      request.setUrl(url)
    }

  }

}


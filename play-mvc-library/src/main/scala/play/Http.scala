package play.api.mvc

import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import play.Result
import play.api.http.HeaderNames


trait Request extends RequestHeader {

}

case class HttpRequest(req: HttpServletRequest, resp: HttpServletResponse) extends RequestHeader {

  def path: String = req.getServletPath + Option(req.getPathInfo).getOrElse("")

  def method: String = req.getMethod

  def cookies = Option(req.getCookies).map(_.map(c => c.getName -> c.getValue).toMap) getOrElse Map()

  def queryString: Map[String, Seq[String]] = {
    Option(req.getQueryString).map {
      _.split("&").foldLeft(Map[String, Seq[String]]().withDefaultValue(Seq.empty)) {
        (map, pair) =>
          val (k, v) = pair.span(_ != '=')
          map.updated(k, map(k) ++ Seq(v.drop(1)))
      }
    }.getOrElse(Map())
  }

  /**
   * The HTTP host (domain, optionally port)
   */
  def host: String = Option(req.getHeader(HeaderNames.HOST)).getOrElse("")

  /**
   * The HTTP domain
   */
  lazy val domain: String = host.split(':').head

}

trait RequestHeader {
  /**
   * The URI path.
   */
  def path: String

  /**
   * The HTTP method.
   */
  def method: String

  def host: String

  /**
   * The parsed query string.
   */
  def queryString: Map[String, Seq[String]]

  def req: HttpServletRequest

  def resp: HttpServletResponse

}

/**
 * Defines a `Call`, which describes an HTTP request and can be used to create links or fill redirect data.
 *
 * These values are usually generated by the reverse router.
 *
 * @param method the request HTTP method
 * @param url the request URL
 */
case class Call(method: String, url: String) {

  /**
   * Transform this call to an absolute URL.
   */
  def absoluteURL(secure: Boolean = false)(implicit request: RequestHeader) = {
    "http" + (if (secure) "s" else "") + "://" + request.host + this.url
  }


  val rand = new java.util.Random()

  /**
   * Append a unique identifier to the URL.
   */
  def unique: Call = new Call(method, if (this.url.contains('?')) this.url + "?" + rand.nextLong() else this.url + "&" + rand.nextLong())


  override def toString = url

}

/**
 * An Handler handles a request.
 */
trait Handler {
  def apply(ctx: RequestHeader): Result
}

/**
 * Reference to an Handler.
 */
class HandlerRef(callValue: => Action, handlerDef: play.core.Router.HandlerDef)(implicit handlerInvoker: play.core.Router.HandlerInvoker) {
  //} extends play.mvc.HandlerRef {


  /**
   * Retrieve a real handler behind this ref.
   */
  def handler: play.api.mvc.Handler = {
    handlerInvoker.call(callValue, handlerDef)
  }

  /**
   * String representation of this Handler.
   */
  lazy val sym = {
    handlerDef.controller + "." + handlerDef.method + "(" + handlerDef.parameterTypes.map(_.getName).mkString(", ") + ")"
  }

  override def toString = {
    "HandlerRef[" + sym + ")]"
  }


}


/**
 * An HTTP cookie.
 *
 * @param name the cookie name
 * @param value the cookie value
 * @param maxAge the cookie expiration date in seconds, `None` for a transient cookie, or a value less than 0 to expire a cookie now
 * @param path the cookie path, defaulting to the root path `/`
 * @param domain the cookie domain
 * @param secure whether this cookie is secured, sent only for HTTPS requests
 * @param httpOnly whether this cookie is HTTP only, i.e. not accessible from client-side JavaScipt code
 */
case class Cookie(name: String, value: String, maxAge: Option[Int] = None, path: String = "/", domain: Option[String] = None, secure: Boolean = false, httpOnly: Boolean = true)

/**
 * A cookie to be discarded.  This contains only the data necessary for discarding a cookie.
 *
 * @param name the name of the cookie to discard
 * @param path the path of the cookie, defaults to the root path
 * @param domain the cookie domain
 * @param secure whether this cookie is secured
 */
case class DiscardingCookie(name: String, path: String = "/", domain: Option[String] = None, secure: Boolean = false) {
  def toCookie = Cookie(name, "", Some(-1), path, domain, secure)
}

/**
 * The HTTP cookies set.
 */
trait Cookies {

  /**
   * Optionally returns the cookie associated with a key.
   */
  def get(name: String): Option[Cookie]

  /**
   * Retrieves the cookie that is associated with the given key.
   */
  def apply(name: String): Cookie = get(name).getOrElse(scala.sys.error("Cookie doesn't exist"))

}
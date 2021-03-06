package org.scalatra

import org.scalatra.ScalatraBase._
import javax.servlet.Filter
import javax.servlet.http.{ HttpServletResponse, HttpServletRequest, HttpServlet }
import scala.util.Failure
import skinny.SkinnyEnv

/**
 * Partially patched ScalatraBase for Skinny Framework.
 *
 * Scalatra runs only the first one of after filters. The others are skipped due to
 * the servlet/filter is already marked as "org.scalatra.ScalatraFilter.afterFilters.Run".
 * This means that when several Scalatra filters is already definied below at ScalatraBootstrap.scala,
 * current ScalatraFilter's after callbacks would be ignored (even though all the before callbacks are called).
 *
 * So We've patched ScalatraBase to ignore "org.scalatra.ScalatraFilter.afterFilters.Run" only for Filters.
 * Hope Scalatra to support ignoring "org.scalatra.ScalatraFilter.afterFilters.Run" option to 3rd party.
 */
trait SkinnyScalatraBase extends ScalatraBase {

  override protected def executeRoutes() {
    var result: Any = null
    var rendered = true

    def runActions = {
      val prehandleException = request.get(PrehandleExceptionKey)
      if (prehandleException.isEmpty) {
        val (rq, rs) = (request, response)
        onCompleted { _ =>
          withRequestResponse(rq, rs) {
            this match {
              case f: Filter if !rq.contains("org.scalatra.ScalatraFilter.afterFilters.Run") =>
                // **** PATCHED ****
                // rq("org.scalatra.ScalatraFilter.afterFilters.Run") = new {}
                runFilters(routes.afterFilters)
              case f: HttpServlet if !rq.contains("org.scalatra.ScalatraServlet.afterFilters.Run") =>
                rq("org.scalatra.ScalatraServlet.afterFilters.Run") = new {}
                runFilters(routes.afterFilters)
              case _ =>
            }
          }
        }
        runFilters(routes.beforeFilters)
        val actionResult = runRoutes(routes(request.requestMethod)).headOption
        // Give the status code handler a chance to override the actionResult
        val r = handleStatusCode(status) getOrElse {
          actionResult orElse matchOtherMethods() getOrElse doNotFound()
        }
        rendered = false
        r
      } else {
        throw prehandleException.get.asInstanceOf[Exception]
      }
    }

    cradleHalt(result = runActions, e => {
      cradleHalt({
        result = errorHandler(e)
        rendered = false
      }, e => {
        runCallbacks(Failure(e))
        try {
          renderUncaughtException(e)
        } finally {
          runRenderCallbacks(Failure(e))
        }
      })
    })

    if (!rendered) renderResponse(result)
  }

  // involuntarily copied to call internal functions

  private[this] def cradleHalt(body: => Any, error: Throwable => Any) = {
    try { body } catch {
      case e: HaltException => renderHaltException(e)
      case e: Throwable => error(e)
    }
  }

  private[this] def matchOtherMethods(): Option[Any] = {
    val allow = routes.matchingMethodsExcept(request.requestMethod, requestPath)
    if (allow.isEmpty) None else liftAction(() => doMethodNotAllowed(allow))
  }

  private[this] def handleStatusCode(status: Int): Option[Any] =
    for {
      handler <- routes(status)
      matchedHandler <- handler(requestPath)
      handlerResult <- invoke(matchedHandler)
    } yield handlerResult

  private def liftAction(action: Action): Option[Any] =
    try {
      Some(action())
    } catch {
      case e: PassException => None
    }

  // TODO fixed?
  override def url(
    path: String,
    params: Iterable[(String, Any)] = Iterable.empty,
    includeContextPath: Boolean = true,
    includeServletPath: Boolean = true,
    absolutize: Boolean = true,
    withSessionId: Boolean = true)(
      implicit request: HttpServletRequest, response: HttpServletResponse): String = {

    try {
      super.url(path, params, includeContextPath, includeServletPath, absolutize)
    } catch {
      case e: NullPointerException =>
        // work around for Scalatra issue
        if (SkinnyEnv.isTest()) "[work around] see https://github.com/scalatra/scalatra/issues/368"
        else throw e
    }
  }

}

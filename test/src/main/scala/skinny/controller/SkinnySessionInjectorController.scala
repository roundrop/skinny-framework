package skinny.controller

import skinny._
import skinny.filter.SkinnySessionFilter

/**
 * Session injector for testing & debugging
 */
private[skinny] object SkinnySessionInjectorController extends SkinnySessionInjectorController {
  put("/skinny-session")(update)
}

/**
 * Session injector for testing & debugging.
 */
trait SkinnySessionInjectorController extends SkinnyController with SkinnySessionFilter {

  /**
   * Injects a value into session.
   *
   * @param format format
   * @return none
   */
  def update()(implicit format: Format = Format.HTML) = {
    if (isProduction) haltWithBody(404)
    else {
      params.foreach {
        case (key, value) =>
          val obj = SessionInjectorController.deserialize(value)
          logger.debug(s"${key} -> ${obj}")
          skinnySession.setAttribute(key, obj)
      }
      skinnySession.save()
    }
  }

}


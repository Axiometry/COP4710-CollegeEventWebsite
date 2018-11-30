package models

import scala.concurrent.Future

import com.mohiva.play.silhouette.api.util.PasswordInfo
import com.mohiva.play.silhouette.api.{ Authorization, Identity, LoginInfo }
import play.api.mvc.Request
import utils.auth.DefaultEnv


sealed trait Role
object Role {
  case object Student extends Role
  case object Admin extends Role
  case object SuperAdmin extends Role
}
case class WithRole(role: Role) extends Authorization[User, DefaultEnv#A] {
  override def isAuthorized[B](identity: User, authenticator: DefaultEnv#A)(implicit request: Request[B]): Future[Boolean] =
    Future.successful(identity.role == role)
}

case class User(
    id: Long,
    username: String,
    email: String,
    university: Option[University],
    loginInfo: LoginInfo,
    passwordInfo: Option[PasswordInfo],
    role: Role) extends Identity {
  override def equals(o:Any) = o match {
    case u: User if id == u.id => true
    case _ => false
  }
  override def hashCode = id.##
}
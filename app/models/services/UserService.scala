package models.services

import scala.concurrent.Future
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.services.IdentityService
import models._

trait UserService extends IdentityService[User] {
  def retrieve(): Future[Seq[User]]
  def retrieve(id: Long): Future[Option[User]]
  def retrieve(loginInfo: LoginInfo): Future[Option[User]]
  def retrieve(username: String): Future[Option[User]]
  def save(user: User): Future[User]
  def nextId(): Future[Long]
}

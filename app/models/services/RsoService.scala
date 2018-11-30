package models.services

import models._

import scala.concurrent.Future

trait RsoService {
  def retrieve(): Future[Seq[Rso]]
  def retrieve(id: Long): Future[Option[Rso]]
  def retrieve(owner: User): Future[Seq[Rso]]
  def save(rso: Rso): Future[Rso]
  def delete(rso: Rso): Future[Unit]
  def nextId(): Future[Long]
}

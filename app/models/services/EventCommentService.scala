package models.services

import models._

import scala.concurrent.Future

trait EventCommentService {
  def retrieve(id: Long): Future[Option[EventComment]]
  def retrieve(event: Event): Future[Seq[EventComment]]
  def retrieve(user: User): Future[Seq[EventComment]]
  def save(comment: EventComment): Future[EventComment]
  def delete(comment: EventComment): Future[Unit]
  def nextId(): Future[Long]
}

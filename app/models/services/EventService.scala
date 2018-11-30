package models.services

import models.Event
import org.joda.time.DateTime

import scala.concurrent.Future

trait EventService {
  def retrieve(): Future[Seq[Event]]
  def retrieve(id: Long): Future[Option[Event]]
  def retrieve(locationName: String, time: DateTime): Future[Option[Event]]
  def save(event: Event): Future[Event]
  def delete(id: Long): Future[Unit]
  def nextId(): Future[Long]
}

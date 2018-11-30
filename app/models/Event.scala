package models

import org.joda.time.DateTime

sealed trait EventVisibility
object EventVisibility {
  case object Public extends EventVisibility
  case object Private extends EventVisibility
  case object Rso extends EventVisibility
}

case class Event(
    id: Long,
    name: String,
    description: String,
    category: String,
    dateTime: DateTime,
    locationName: String,
    locationCoords: String,
    contactPhone: String,
    contactEmail: String,
    visibility: EventVisibility,
    approved: Boolean,
    owner: User,
    rso: Option[Rso]) {
  override def equals(o:Any) = o match {
    case e: Event if id == e.id => true
    case _ => false
  }
  override def hashCode = id.##
}

case class EventComment(id: Long, event: Event, user: User, rating: Int, body: String)
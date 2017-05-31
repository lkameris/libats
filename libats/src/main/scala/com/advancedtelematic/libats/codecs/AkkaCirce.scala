/**
 * Copyright: Copyright (C) 2016, ATS Advanced Telematic Systems GmbH
 * License: MPL-2.0
 */
package com.advancedtelematic.libats.codecs

import cats.syntax.either._
import io.circe._
import io.circe.{Decoder, Encoder}
import java.time.Instant
import java.time.format.{DateTimeFormatter, DateTimeParseException}
import java.time.temporal.ChronoField
import java.util.UUID

trait CirceDateTime {
  implicit val dateTimeEncoder : Encoder[Instant] = Encoder.instance[Instant] { instant =>
    Json.fromString {
      instant
        .`with`(ChronoField.MILLI_OF_SECOND, 0)
        .`with`(ChronoField.NANO_OF_SECOND, 0)
        .toString
    }
  }

  implicit val dateTimeDecoder : Decoder[Instant] = Decoder.instance { c =>
    c.focus.flatMap(_.asString) match {
      case None       => Either.left(DecodingFailure("DataTime", c.history))
      case Some(date) =>
        try {
          val fmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME
          val nst = Instant.from(fmt.parse(date))
          Either.right(nst)
        } catch {
          case t: DateTimeParseException =>
            Either.left(DecodingFailure("DateTime", c.history))
        }
    }
  }
}

object CirceDateTime extends CirceDateTime

trait CirceUuid {
  implicit val javaUuidEncoder : Encoder[UUID] = Encoder[String].contramap(_.toString)
  implicit val javaUuidDecoder : Decoder[UUID] = Decoder[String].map(UUID.fromString)
}

object CirceUuid extends CirceUuid

object AkkaCirce extends CirceDateTime
  with CirceUuid
  with CirceAnyVal
  with CirceRefined

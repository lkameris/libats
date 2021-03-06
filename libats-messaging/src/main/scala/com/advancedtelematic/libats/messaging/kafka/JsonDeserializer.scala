/*
 * Copyright: Copyright (C) 2016, ATS Advanced Telematic Systems GmbH
 *  License: MPL-2.0
 */

package com.advancedtelematic.libats.messaging.kafka

import java.nio.ByteBuffer
import java.util

import cats.syntax.either._
import io.circe.Decoder
import io.circe.jawn._
import org.apache.kafka.common.serialization.Deserializer
import org.slf4j.LoggerFactory

import scala.util.control.NoStackTrace

class JsonDeserializerException(msg: String) extends Exception(msg) with NoStackTrace

class JsonDeserializer[T](decoder: Decoder[T], throwException: Boolean = false) extends Deserializer[T] {
  private lazy val _logger = LoggerFactory.getLogger(this.getClass)


  override def deserialize(topic: String, data: Array[Byte]): T = {
    val buffer = ByteBuffer.wrap(data)

    val msgXor = parseByteBuffer(buffer).flatMap(_.as[T](decoder))

    msgXor match {
      case Right(v) => v
      case Left(ex) =>
        if (throwException) {
          throw new JsonDeserializerException(s"Could not parse msg from $topic: ${ex.getMessage}")
        } else {
          _logger.error(s"Could not parse msg from $topic", ex)
          null.asInstanceOf[T]
        }
    }
  }

  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = ()

  override def close(): Unit = ()
}

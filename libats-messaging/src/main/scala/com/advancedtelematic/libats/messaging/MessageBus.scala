package com.advancedtelematic.libats.messaging

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import cats.syntax.either._
import com.advancedtelematic.libats.messaging.kafka.KafkaClient
import com.typesafe.config.ConfigException.Missing
import com.typesafe.config.Config
import com.advancedtelematic.libats.messaging_datatype.Messages._
import MessageListener.{CommittableMsg, KafkaMsg}
import com.advancedtelematic.libats.messaging_datatype.MessageLike
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait MessageBusPublisher {
  lazy private val logger = LoggerFactory.getLogger(this.getClass)

  def publish[T](msg: T)(implicit ex: ExecutionContext, messageLike: MessageLike[T]): Future[Unit]

  def publishSafe[T](msg: T)(implicit ec: ExecutionContext, messageLike: MessageLike[T]): Future[Try[Unit]] = {
    publish(msg)
      .map { _ =>
        logger.info(s"published ${messageLike.streamName} - ${messageLike.id(msg)}")
        Success(())
      }
      .recover { case t =>
        logger.error(s"Could not publish $msg msg to bus", t)
        Failure(t)
    }
  }
}

object MessageBusPublisher {
  def ignore = new MessageBusPublisher {
    lazy private val _logger = LoggerFactory.getLogger(this.getClass)

    override def publish[T](msg: T)(implicit ex: ExecutionContext, messageLike: MessageLike[T]): Future[Unit] = {
      _logger.info(s"Ignoring message publish to bus")
      Future.successful(())
    }
  }

  implicit class FuturePipeToBus[T](v: Future[T]) {
    def pipeToBus[M](messageBusPublisher: MessageBusPublisher)
                    (fn: T => M)(implicit ec: ExecutionContext, messageLike: MessageLike[M]): Future[T] = {
      v.andThen {
        case Success(futureResult) => messageBusPublisher.publish(fn(futureResult))
      }
    }
  }
}

object MessageBus {
  val DEFAULT_CLIENT_BUFFER_SIZE = 1024 // number of msgs

  lazy val log = LoggerFactory.getLogger(this.getClass)

  def subscribe[T](system: ActorSystem, config: Config)
                  (implicit messageLike: MessageLike[T]): Throwable Either Source[T, NotUsed] = {
    config.getString("messaging.mode").toLowerCase().trim match {
      case "kafka" =>
        log.info("Starting messaging mode: Kafka")
        log.info(s"Using stream name: ${messageLike.streamName}")
        KafkaClient.source(system, config)(messageLike)
      case "local" | "test" =>
        log.info("Using local event bus")
        Right(LocalMessageBus.subscribe(system)(messageLike))
      case mode =>
        Left(new Missing(s"Unknown messaging mode specified ($mode)"))
    }
  }

  def subscribeCommittable[T](config: Config)
                                           (implicit messageLike: MessageLike[T], system: ActorSystem)
  : Source[CommittableMsg[T], NotUsed] = {
    config.getString("messaging.mode").toLowerCase().trim match {
      case "kafka" =>
        log.info("Starting messaging mode: Kafka")
        log.info(s"Using stream name: ${messageLike.streamName}")
        KafkaClient.committableSource[T](config)(messageLike, system) match {
          case Right(s) => s.map(msg => new KafkaMsg(msg))
          case Left(err) => throw err
        }
      case "local" | "test" =>
        log.info("Using local event bus")
        LocalMessageBus.subscribeCommittable(system)(messageLike)
      case mode =>
        throw new Missing(s"Unknown messaging mode specified ($mode)")
    }
  }

  def publisher(system: ActorSystem, config: Config): Throwable Either MessageBusPublisher = {
    config.getString("messaging.mode").toLowerCase().trim match {
      case "kafka" =>
        log.info("Starting messaging mode: Kafka")
        KafkaClient.publisher(system, config)
      case "local" | "test" =>
        log.info("Using local message bus")
        Right(LocalMessageBus.publisher(system))
      case mode =>
        Left(new Missing(s"Unknown messaging mode specified ($mode)"))
    }
  }
}

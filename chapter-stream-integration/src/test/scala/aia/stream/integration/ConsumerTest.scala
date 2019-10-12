package aia.stream.integration

import java.io.File

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.alpakka.amqp.{ AmqpConnectionUri, AmqpSinkSettings, NamedQueueSourceSettings }
import akka.stream.scaladsl.{ Keep, RunnableGraph, Sink, Source }
import akka.testkit.TestKit
import com.rabbitmq.client.{ AMQP, ConnectionFactory }
import io.arivera.oss.embedded.rabbitmq.{ EmbeddedRabbitMq, EmbeddedRabbitMqConfig, PredefinedVersion }
import org.apache.commons.io.FileUtils
import org.scalatest.{ BeforeAndAfterAll, MustMatchers, WordSpecLike }

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }

class ConsumerTest extends TestKit(ActorSystem("ConsumerTest"))
  with WordSpecLike with BeforeAndAfterAll with MustMatchers {

  implicit val materializer = ActorMaterializer()

  import Orders._

  val dir = new File("messages")
  val msgFile = new File(dir, "msg1.xml")

  val rabbitMq: EmbeddedRabbitMq = {
    val config = new EmbeddedRabbitMqConfig.Builder()
      .downloadFolder(new File("./rabbitmq/downloads"))
      .extractionFolder(new File(s"${System.getProperty("user.dir")}/rabbitmq/servers"))
      .version(PredefinedVersion.V3_6_9)
      .useCachedDownload(true)
      .deleteDownloadedFileOnErrors(false)
      .port(8899)
      .rabbitMqServerInitializationTimeoutInMillis(60000)
      .build()

    val mq = new EmbeddedRabbitMq(config)
    mq.start()
    mq
  }

  override def beforeAll(): Unit = {
    if (!dir.exists()) {
      dir.mkdir()
    }

    if (msgFile.exists()) {
      msgFile.delete()
    }
  }

  override def afterAll(): Unit = {
    system.terminate()
    rabbitMq.stop()
    FileUtils.deleteDirectory(dir)
  }

  "Consumer" must {
    "pickup xml files" in {

      val consumer: RunnableGraph[Future[Order]] =
        FileXmlOrderSource.watch(dir.toPath)
          .toMat(Sink.head[Order])(Keep.right)

      val consumedOrder: Future[Order] = consumer.run()

      val msg = Order("me", "Akka in Action", 10)
      val xml = <order>
        <customerId>
          {msg.customerId}
        </customerId>
        <productId>
          {msg.productId}
        </productId>
        <number>
          {msg.number}
        </number>
      </order>
      val msgFile = new File(dir, "msg1.xml")

      FileUtils.write(msgFile, xml.toString())

      Await.result(consumedOrder, 10.seconds) must be(msg)
    }
    "pickup xml AMQP" in {
      val queueName = "xmlTest"
      val amqpSourceSettings =
        NamedQueueSourceSettings(
          AmqpConnectionUri("amqp://localhost:8899"),
          queueName
        )

      val msg = Order("me", "Akka in Action", 10)
      val xml = <order>
        <customerId>
          {msg.customerId}
        </customerId>
        <productId>
          {msg.productId}
        </productId>
        <number>
          {msg.number}
        </number>
      </order>

      sendMQMessage(queueName, xml.toString)

      val consumer: RunnableGraph[Future[Order]] =
        AmqpXmlOrderSource(amqpSourceSettings)
          .toMat(Sink.head)(Keep.right)

      val consumedOrder: Future[Order] = consumer.run()
      Await.result(consumedOrder, 10 seconds) must be(msg)
    }
  }

  "The Producer" must {
    "send msg using AMQP" in {
      implicit val executionContext = system.dispatcher

      val queueName = "xmlTest"

      val amqpSinkSettings =
        AmqpSinkSettings(
          AmqpConnectionUri("amqp://localhost:8899"),
          routingKey = Some(queueName)
        )

      val msg = new Order("me", "Akka in Action", 10)

      val producer: RunnableGraph[NotUsed] =
        Source.single(msg)
          .to(AmqpXmlOrderSink(amqpSinkSettings))

      val amqpSourceSettings =
        NamedQueueSourceSettings(
          AmqpConnectionUri("amqp://localhost:8899"),
          queueName
        )

      val consumer: RunnableGraph[Future[Order]] =
        AmqpXmlOrderSource(amqpSourceSettings)
          .toMat(Sink.head)(Keep.right)

      producer.run()

      val consumedOrder: Future[Order] = consumer.run()
      Await.result(consumedOrder, 10 seconds) must be(msg)
    }
  }

  def sendMQMessage(queueName: String, msg: String): Unit = {

    // Create a ConnectionFactory
    val connectionFactory = new ConnectionFactory
    connectionFactory.setUri("amqp://localhost:8899")

    // Create a Connection
    val connection = connectionFactory.newConnection()

    // Create a Channel
    val channel = connection.createChannel()
    channel.queueDeclare(queueName, true, false, false, null)

    // send the message
    channel.basicPublish("", queueName,
      new AMQP.BasicProperties.Builder().build(),
      msg.getBytes()
    )

    // Clean up
    channel.close()
    connection.close()
  }
}

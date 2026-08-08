package com.wayden.messenger.common.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class HttpAuditQueueDispatcher {

  private static final Logger LOG = Logger.getLogger(HttpAuditQueueDispatcher.class);

  private final HttpAuditEventSink eventSink;
  private final HttpAuditDeadLetterHandler deadLetterHandler;
  private final ObjectMapper objectMapper;
  private final boolean asyncEnabled;
  private final int queueCapacity;
  private final boolean rabbitEnabled;
  private final String rabbitHost;
  private final int rabbitPort;
  private final String rabbitUsername;
  private final Optional<String> rabbitPassword;
  private final String rabbitVhost;
  private final String rabbitExchange;
  private final String rabbitRoutingKey;
  private final String rabbitQueue;
  private final boolean rabbitConsumerEnabled;
  private final int rabbitPrefetch;

  private volatile boolean running;
  private volatile boolean rabbitActive;
  private BlockingQueue<HttpAuditEvent> localQueue;
  private Thread localWorkerThread;
  private Thread rabbitConsumerThread;
  private Connection rabbitConnection;
  private Channel rabbitPublishChannel;
  private Channel rabbitConsumeChannel;

  @Inject
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "ObjectMapper is a CDI-managed singleton dependency intentionally shared across components.")
  public HttpAuditQueueDispatcher(
      HttpAuditEventSink eventSink,
      HttpAuditDeadLetterHandler deadLetterHandler,
      ObjectMapper objectMapper,
      @ConfigProperty(name = "chat.audit.async.enabled", defaultValue = "true")
          boolean asyncEnabled,
      @ConfigProperty(name = "chat.audit.async.queue-capacity", defaultValue = "1024")
          int queueCapacity,
      @ConfigProperty(name = "chat.audit.rabbitmq.enabled", defaultValue = "false")
          boolean rabbitEnabled,
      @ConfigProperty(name = "chat.audit.rabbitmq.host", defaultValue = "localhost")
          String rabbitHost,
      @ConfigProperty(name = "chat.audit.rabbitmq.port", defaultValue = "5672") int rabbitPort,
      @ConfigProperty(name = "chat.audit.rabbitmq.username", defaultValue = "wl_chat_queue")
          String rabbitUsername,
      @ConfigProperty(name = "chat.audit.rabbitmq.password") Optional<String> rabbitPassword,
      @ConfigProperty(name = "chat.audit.rabbitmq.vhost", defaultValue = "/") String rabbitVhost,
      @ConfigProperty(name = "chat.audit.rabbitmq.exchange", defaultValue = "audit.events")
          String rabbitExchange,
      @ConfigProperty(name = "chat.audit.rabbitmq.routing-key", defaultValue = "audit.completed")
          String rabbitRoutingKey,
      @ConfigProperty(name = "chat.audit.rabbitmq.queue", defaultValue = "audit.events")
          String rabbitQueue,
      @ConfigProperty(name = "chat.audit.rabbitmq.consumer.enabled", defaultValue = "true")
          boolean rabbitConsumerEnabled,
      @ConfigProperty(name = "chat.audit.rabbitmq.consumer.prefetch", defaultValue = "25")
          int rabbitPrefetch) {
    this.eventSink = eventSink;
    this.deadLetterHandler = deadLetterHandler;
    this.objectMapper = objectMapper;
    this.asyncEnabled = asyncEnabled;
    this.queueCapacity = queueCapacity;
    this.rabbitEnabled = rabbitEnabled;
    this.rabbitHost = rabbitHost;
    this.rabbitPort = rabbitPort;
    this.rabbitUsername = rabbitUsername;
    this.rabbitPassword = rabbitPassword;
    this.rabbitVhost = rabbitVhost;
    this.rabbitExchange = rabbitExchange;
    this.rabbitRoutingKey = rabbitRoutingKey;
    this.rabbitQueue = rabbitQueue;
    this.rabbitConsumerEnabled = rabbitConsumerEnabled;
    this.rabbitPrefetch = rabbitPrefetch;
  }

  HttpAuditQueueDispatcher(
      HttpAuditEventSink eventSink,
      HttpAuditDeadLetterHandler deadLetterHandler,
      boolean asyncEnabled,
      int queueCapacity) {
    this(
        eventSink,
        deadLetterHandler,
        new ObjectMapper(),
        asyncEnabled,
        queueCapacity,
        false,
        "localhost",
        5672,
        "wl_chat_queue",
        Optional.empty(),
        "/",
        "audit.events",
        "audit.completed",
        "audit.events",
        false,
        25);
  }

  @PostConstruct
  void start() {
    running = true;
    if (rabbitEnabled && initializeRabbit()) {
      rabbitActive = true;
      if (rabbitConsumerEnabled) {
        startRabbitConsumer();
      }
      LOG.infof(
          "HTTP audit RabbitMQ transport enabled host=%s port=%d exchange=%s queue=%s",
          rabbitHost, rabbitPort, rabbitExchange, rabbitQueue);
      return;
    }

    if (asyncEnabled) {
      localQueue = new LinkedBlockingQueue<>(queueCapacity);
      localWorkerThread = new Thread(this::runLocalWorker, "http-audit-dispatcher");
      localWorkerThread.setDaemon(true);
      localWorkerThread.start();
      LOG.infof("HTTP audit local async dispatcher started capacity=%d", queueCapacity);
    }
  }

  public void submit(HttpAuditEvent event) {
    if (rabbitActive) {
      publishToRabbit(event);
      return;
    }

    if (!asyncEnabled) {
      persistWithFailOpen(event);
      return;
    }

    if (localQueue == null || !localQueue.offer(event)) {
      handleDeadLetter(event, new IllegalStateException("Audit queue is full or unavailable"));
    }
  }

  @PreDestroy
  void shutdown() {
    running = false;
    if (localWorkerThread != null) {
      localWorkerThread.interrupt();
      try {
        localWorkerThread.join(500L);
      } catch (InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
      }
    }
    if (rabbitConsumerThread != null) {
      rabbitConsumerThread.interrupt();
      try {
        rabbitConsumerThread.join(500L);
      } catch (InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
      }
    }
    closeRabbitResources();
  }

  private void runLocalWorker() {
    while (running || (localQueue != null && !localQueue.isEmpty())) {
      try {
        HttpAuditEvent event = localQueue.poll(200, TimeUnit.MILLISECONDS);
        if (event == null) {
          continue;
        }
        persistWithFailOpen(event);
      } catch (InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
        break;
      }
    }
  }

  private boolean initializeRabbit() {
    String password = rabbitPassword.map(String::trim).orElse("");
    if (password.isBlank()) {
      LOG.warn(
          "RabbitMQ audit transport enabled but password is empty; falling back to local mode");
      return false;
    }

    try {
      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost(rabbitHost);
      factory.setPort(rabbitPort);
      factory.setUsername(rabbitUsername);
      factory.setPassword(password);
      factory.setVirtualHost(rabbitVhost);
      rabbitConnection = factory.newConnection("chat-backend-audit");
      rabbitPublishChannel = rabbitConnection.createChannel();
      rabbitConsumeChannel = rabbitConnection.createChannel();

      rabbitPublishChannel.exchangeDeclare(rabbitExchange, "topic", true);
      rabbitPublishChannel.queueDeclare(rabbitQueue, true, false, false, null);
      rabbitPublishChannel.queueBind(rabbitQueue, rabbitExchange, rabbitRoutingKey);
      rabbitConsumeChannel.basicQos(Math.max(1, rabbitPrefetch));
      return true;
    } catch (Exception exception) {
      LOG.warn(
          "Failed to initialize RabbitMQ audit transport; falling back to local mode", exception);
      closeRabbitResources();
      return false;
    }
  }

  private void startRabbitConsumer() {
    rabbitConsumerThread = new Thread(this::runRabbitConsumer, "http-audit-rabbit-consumer");
    rabbitConsumerThread.setDaemon(true);
    rabbitConsumerThread.start();
  }

  private void runRabbitConsumer() {
    while (running && rabbitActive) {
      try {
        if (rabbitConsumeChannel == null || !rabbitConsumeChannel.isOpen()) {
          LOG.warn("RabbitMQ consumer channel is not open; stopping consumer loop");
          return;
        }

        GetResponse response = rabbitConsumeChannel.basicGet(rabbitQueue, false);
        if (response == null) {
          TimeUnit.MILLISECONDS.sleep(150);
          continue;
        }

        long deliveryTag = response.getEnvelope().getDeliveryTag();
        try {
          HttpAuditEvent event = objectMapper.readValue(response.getBody(), HttpAuditEvent.class);
          LOG.infof(
              "AUDIT QUEUE consume requestId=%s eventType=%s",
              event.requestId(), event.eventType());
          eventSink.persist(event);
          rabbitConsumeChannel.basicAck(deliveryTag, false);
          LOG.infof(
              "AUDIT DB persist requestId=%s eventType=%s", event.requestId(), event.eventType());
        } catch (Exception processingException) {
          safeNack(deliveryTag);
          handleDeadLetter(
              null,
              new IllegalStateException(
                  "Failed to process consumed audit event", processingException));
        }
      } catch (InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
        return;
      } catch (Exception exception) {
        LOG.warn("RabbitMQ audit consumer loop failure", exception);
      }
    }
  }

  private void publishToRabbit(HttpAuditEvent event) {
    try {
      if (rabbitPublishChannel == null || !rabbitPublishChannel.isOpen()) {
        throw new IllegalStateException("RabbitMQ publish channel is not open");
      }
      byte[] payload = objectMapper.writeValueAsBytes(event);
      AMQP.BasicProperties properties =
          new AMQP.BasicProperties.Builder()
              .contentType("application/json")
              .deliveryMode(2)
              .build();
      synchronized (this) {
        rabbitPublishChannel.basicPublish(rabbitExchange, rabbitRoutingKey, properties, payload);
      }
      LOG.infof(
          "AUDIT QUEUE publish requestId=%s eventType=%s exchange=%s routingKey=%s",
          event.requestId(), event.eventType(), rabbitExchange, rabbitRoutingKey);
    } catch (Exception exception) {
      handleDeadLetter(
          event, new IllegalStateException("Failed to publish audit event", exception));
    }
  }

  private void safeNack(long deliveryTag) {
    try {
      if (rabbitConsumeChannel != null && rabbitConsumeChannel.isOpen()) {
        rabbitConsumeChannel.basicNack(deliveryTag, false, false);
      }
    } catch (IOException ioException) {
      LOG.warn("Failed to nack consumed audit message", ioException);
    }
  }

  private void closeRabbitResources() {
    rabbitActive = false;
    closeQuietly(rabbitConsumeChannel);
    rabbitConsumeChannel = null;
    closeQuietly(rabbitPublishChannel);
    rabbitPublishChannel = null;
    if (rabbitConnection != null) {
      try {
        rabbitConnection.close();
      } catch (Exception ignored) {
        // no-op
      }
      rabbitConnection = null;
    }
  }

  private void closeQuietly(Channel channel) {
    if (channel == null) {
      return;
    }
    try {
      channel.close();
    } catch (Exception ignored) {
      // no-op
    }
  }

  private void persistWithFailOpen(HttpAuditEvent event) {
    try {
      eventSink.persist(event);
    } catch (RuntimeException exception) {
      handleDeadLetter(event, exception);
    }
  }

  private void handleDeadLetter(HttpAuditEvent event, Exception exception) {
    try {
      if (event != null) {
        deadLetterHandler.handle(event, exception);
      } else {
        LOG.warn("Audit dead-letter event unavailable for failed processing", exception);
      }
    } catch (RuntimeException deadLetterException) {
      LOG.error("HTTP audit dead-letter handler failed", deadLetterException);
    }
  }
}

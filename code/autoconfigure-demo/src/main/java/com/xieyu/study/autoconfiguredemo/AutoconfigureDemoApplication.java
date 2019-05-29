package com.xieyu.study.autoconfiguredemo;

import com.xieyu.study.autoconfigure.HornetQConfigurationCustomizer;
import org.hornetq.jms.server.config.JMSConfiguration;
import org.hornetq.jms.server.config.impl.JMSConfigurationImpl;
import org.hornetq.jms.server.config.impl.JMSQueueConfigurationImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@SpringBootApplication
public class AutoconfigureDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutoconfigureDemoApplication.class, args);
    }

    @Bean
    public JMSConfiguration myJmsConfiguration() {
        JMSConfiguration configuration = new JMSConfigurationImpl();
        configuration.getQueueConfigurations().add(new JMSQueueConfigurationImpl("testQueue", null, false));
        return configuration;
    }

    @Bean
    public HornetQConfigurationCustomizer myHornetQCustomizer() {
        return configuration -> {
            configuration.setJMXManagementEnabled(true);
            configuration.setJMXDomain("org.foo");
        };
    }

    @Service
    static class MessageProducer implements CommandLineRunner {

        private static final Logger logger = LoggerFactory.getLogger(MessageProducer.class);

        private final JmsTemplate jmsTemplate;

        @Autowired
        public MessageProducer(JmsTemplate jmsTemplate) {
            this.jmsTemplate = jmsTemplate;
        }


        @Override
        public void run(String... args) throws Exception {
            process("Hello World");
        }

        public void process(String msg) {
            logger.info("=============== Sending " + msg);
            this.jmsTemplate.convertAndSend("testQueue", msg);
        }
    }

    @Component
    static class MessageHandler {
        private static final Logger logger = LoggerFactory.getLogger(MessageHandler.class);

        @JmsListener(destination = "testQueue")
        public void processMsg(String msg) {
            logger.info("=============== Received " + msg);
        }
    }
}

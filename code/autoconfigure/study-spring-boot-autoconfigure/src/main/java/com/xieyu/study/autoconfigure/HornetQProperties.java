package com.xieyu.study.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hornetq")
public class HornetQProperties {
    private HornetQMode mode;
    private String host = "localhost";
    private int port = 5445;
    private final Embedded embedded = new Embedded();

    public HornetQMode getMode() {
        return mode;
    }

    public void setMode(HornetQMode mode) {
        this.mode = mode;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public Embedded getEmbedded() {
        return embedded;
    }

    public static class Embedded {
        private boolean enabled = true;
        private boolean persistent;
        private String dataDirectory;
        private String[] queues = new String[0];
        private String[] topics = new String[0];

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isPersistent() {
            return persistent;
        }

        public void setPersistent(boolean persistent) {
            this.persistent = persistent;
        }

        public String getDataDirectory() {
            return dataDirectory;
        }

        public void setDataDirectory(String dataDirectory) {
            this.dataDirectory = dataDirectory;
        }

        public String[] getQueues() {
            return queues;
        }

        public void setQueues(String[] queues) {
            this.queues = queues;
        }

        public String[] getTopics() {
            return topics;
        }

        public void setTopics(String[] topics) {
            this.topics = topics;
        }
    }
}

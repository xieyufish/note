package com.xieyu.study.autoconfigure;

import org.hornetq.core.config.Configuration;

public interface HornetQConfigurationCustomizer {

    void customize(Configuration configuration);
}

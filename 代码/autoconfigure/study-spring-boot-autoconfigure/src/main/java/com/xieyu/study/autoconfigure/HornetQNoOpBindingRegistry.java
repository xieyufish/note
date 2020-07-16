package com.xieyu.study.autoconfigure;

import org.hornetq.spi.core.naming.BindingRegistry;

public class HornetQNoOpBindingRegistry implements BindingRegistry {
    @Override
    public Object getContext() {
        return this;
    }

    @Override
    public void setContext(Object o) {

    }

    @Override
    public Object lookup(String s) {
        return null;
    }

    @Override
    public boolean bind(String s, Object o) {
        return false;
    }

    @Override
    public void unbind(String s) {

    }

    @Override
    public void close() {

    }
}

package org.apache.solr.client.solrj.embedded;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.SortedMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.servlet.DispatcherType;

import lux.solr.LuxDispatchFilter;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.component.LifeCycle.Listener;

/*
 Licensed to the Apache Software Foundation (ASF) under one or more
 contributor license agreements.  See the NOTICE file distributed with
 this work for additional information regarding copyright ownership.
 The ASF licenses this file to You under the Apache License, Version 2.0
 (the "License"); you may not use this file except in compliance with
 the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
*/

/**
 Run solr using jetty
 
 This is a copy of JettySolrRunner that uses LuxDispatchFilter in place of
 SolrDispatchFilter
 
 @since solr 1.3
*/
public class LuxJettySolrRunner extends JettySolrRunner {


    public LuxJettySolrRunner(String solrHome, String context, int port, String
            solrConfigFilename, String schemaFileName, boolean stopAtShutdown,
            SortedMap<ServletHolder, String> extraServlets) { 
        
        super(solrHome, context, port, solrConfigFilename, schemaFileName, stopAtShutdown, extraServlets);
 
        final ServletContextHandler root = (ServletContextHandler) server.getHandler();
 
        Field listenersField;
        try {
            listenersField = AbstractLifeCycle.class.getDeclaredField ("_listeners");
            listenersField.setAccessible(true);
            CopyOnWriteArrayList<?> listeners = (CopyOnWriteArrayList<?>) listenersField.get(server); 
            listeners.clear();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        
        server.addLifeCycleListener(new Listener() {
            @Override
            public void lifeCycleStopping(LifeCycle arg0) {
              System.clearProperty("hostPort");
            }

            @Override
            public void lifeCycleStopped(LifeCycle arg0) {}

            @Override
            public void lifeCycleStarting(LifeCycle arg0) {
              synchronized (LuxJettySolrRunner.this) {
                  set ("waitOnSolr", true);
                  LuxJettySolrRunner.this.notify();
              }
            }

            @Override
            public void lifeCycleStarted(LifeCycle arg0) {
                int lp = getFirstConnectorPort();
                set ("lastPort", lp);
                System.setProperty("hostPort", Integer.toString(lp));
                dispatchFilter = root.addFilter(LuxDispatchFilter.class, "*", EnumSet.of(DispatcherType.REQUEST) );
              /*
              for (ServletHolder servletHolder : extraServlets.keySet()) {
                String pathSpec = extraServlets.get(servletHolder);
                root.addServlet(servletHolder, pathSpec);
              }
              */
                System.clearProperty("solr.solr.home");
            }

            @Override
            public void lifeCycleFailure(LifeCycle arg0, Throwable arg1) {
                System.clearProperty("hostPort");
            }
            
        } ); 
    }
    
    private int getFirstConnectorPort() {
        Connector[] conns = server.getConnectors();
        if (0 == conns.length) {
          throw new RuntimeException("Jetty Server has no Connectors");
        }
        return conns[0].getLocalPort();
      }

    /**
     * Use reflection to set a field value that is otherwise inaccessible
     * @param fieldName
     * @param value
     */
    private void set(String fieldName, Object value) {
        Field field;
        try {
            field = JettySolrRunner.class.getDeclaredField (fieldName);
            field.setAccessible(true);
            field.set(this, value);
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}

/*
 * Copyright 2016 CREATE-NET
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package iot.agile.http.ws;

import java.io.IOException;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.api.Session;

import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.DBusSigHandler;
import org.freedesktop.dbus.exceptions.DBusException;

import iot.agile.Device;
import iot.agile.Device.NewSubscribeValueSignal;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Csaba Kiraly <kiraly@fbk.eu>
 */
@WebSocket
public class AgileWebSocketAdapter extends WebSocketAdapter {

  private Session session;
  private String deviceID = null;
  private String sensorName = null;
  private DBusSigHandler sigHandler;

  private ObjectMapper mapper = new ObjectMapper();

  @Override
  public void onWebSocketConnect(Session sess) {
    session = sess;
    String uripath = sess.getUpgradeRequest().getRequestURI().getPath();
    System.out.printf("New websocket connection from %s for %s %n", sess.getRemoteAddress(), uripath);

    Pattern p = Pattern.compile("^/ws/device/([^/]+)/([^/]+)/subscribe");
    Matcher m = p.matcher(uripath);

    try {
      DBusConnection connection = DBusConnection.getConnection(DBusConnection.SESSION);

      if (m.matches()) {
        deviceID = m.group(1);
        sensorName = m.group(2);
        sigHandler = new DBusSigHandler<Device.NewSubscribeValueSignal>() {
          @Override
          public void handle(NewSubscribeValueSignal sig) {
            if (sig.record.getDeviceID().equals(deviceID) && sig.record.getComponentID().equals(sensorName)) {
              System.out.printf("http: New value %s%n", sig.record);
              try {
                session.getRemote().sendString(mapper.writeValueAsString(sig.record));
              } catch (IOException e) {
                e.printStackTrace();
              }
            }
          }
        };
        connection.addSigHandler(Device.NewSubscribeValueSignal.class, sigHandler);

        String busname = Device.AGILE_INTERFACE;
        String path = "/" + Device.AGILE_INTERFACE.replace(".", "/")  + "/" + deviceID;
        Device device = connection.getRemoteObject(busname, path, Device.class);
        device.Subscribe(sensorName);

      } else {

        sigHandler = new DBusSigHandler<Device.NewSubscribeValueSignal>() {
          @Override
          public void handle(NewSubscribeValueSignal sig) {
            try {
              session.getRemote().sendString(mapper.writeValueAsString(sig.record));
            } catch (IOException e) {
              e.printStackTrace();
            }
          }
        };
        connection.addSigHandler(Device.NewSubscribeValueSignal.class, sigHandler);
      }
    } catch (DBusException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  @Override
  public void onWebSocketClose(int statusCode, String reason) {
    try {
      DBusConnection connection = DBusConnection.getConnection(DBusConnection.SESSION);
      connection.removeSigHandler(Device.NewSubscribeValueSignal.class, sigHandler);

      if (deviceID != null) {
        System.out.printf("ws: closing %s/%s reason:%s%n", deviceID, sensorName, reason);

        String busname = Device.AGILE_INTERFACE;
        String path = "/" + Device.AGILE_INTERFACE.replace(".", "/")  + "/" + deviceID;
        Device device = connection.getRemoteObject(busname, path, Device.class);
        device.Unsubscribe(sensorName);
      } else {
        System.out.printf("ws: closing reason:%s%n", deviceID, sensorName, reason);
      }
    } catch (DBusException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
}

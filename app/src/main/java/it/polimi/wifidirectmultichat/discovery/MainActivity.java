
package it.polimi.wifidirectmultichat.discovery;

/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.net.wifi.p2p.WifiP2pManager.DnsSdServiceResponseListener;
import android.net.wifi.p2p.WifiP2pManager.DnsSdTxtRecordListener;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import it.polimi.wifidirectmultichat.discovery.chatmessages.WiFiChatFragment;
import it.polimi.wifidirectmultichat.discovery.chatmessages.waitingtosend.WaitingToSendQueue;
import it.polimi.wifidirectmultichat.discovery.services.ServiceList;
import it.polimi.wifidirectmultichat.discovery.services.WiFiP2pServicesFragment;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import it.polimi.wifidirectmultichat.R;
import it.polimi.wifidirectmultichat.discovery.services.WiFiP2pService;
import it.polimi.wifidirectmultichat.discovery.services.WiFiServicesAdapter;
import it.polimi.wifidirectmultichat.discovery.socketmanagers.ChatManager;
import it.polimi.wifidirectmultichat.discovery.socketmanagers.ClientSocketHandler;
import it.polimi.wifidirectmultichat.discovery.socketmanagers.GroupOwnerSocketHandler;
import lombok.Getter;
import lombok.Setter;

/**
 * Main Activity.
 * <p/>
 * Created by Stefano Cappa on 04/02/15, based on google code samples.
 */
public class MainActivity extends ActionBarActivity implements
        WiFiP2pServicesFragment.DeviceClickListener,
        WiFiChatFragment.CallbackActivity,
        Handler.Callback,
        ConnectionInfoListener {

    private static final String TAG = "MainActivity";

    @Setter private boolean connected = false;
    @Getter private int tabNum = 1;
    @Getter @Setter private boolean blockForcedDiscoveryInBroadcastReceiver = false;
    private boolean discoveryStatus = true;

    @Getter private TabFragment tabFragment;
    @Getter @Setter private Toolbar toolbar;

    private WifiP2pManager manager;
    private WifiP2pDnsSdServiceRequest serviceRequest;
    private Channel channel;

    private final IntentFilter intentFilter = new IntentFilter();
    private BroadcastReceiver receiver = null;

    private Thread socketHandler;
    private final Handler handler = new Handler(this);


    /**
     * Method to get the {@link android.os.Handler}.
     * @return The handler.
     */
    public Handler getHandler() {
        return handler;
    }


    /**
     * Method called by WiFiChatFragment using the
     * {@link it.polimi.wifidirectmultichat.discovery.chatmessages.WiFiChatFragment.CallbackActivity}
     * interface, implemented here, by this class.
     * If the wifiP2pService is null, this method return directly, without doing anything.
     * @param wifiP2pService A {@link it.polimi.wifidirectmultichat.discovery.services.WiFiP2pService}
     *                       object that represents the device in which you want to connect.
     */
    @Override
    public void reconnectToService(WiFiP2pService wifiP2pService) {
        if (wifiP2pService != null) {
            //tabnum lo setto a caso, tanto il programma capisce da solo qual'e' quello corretto
            Log.d(TAG, "reconnectToService called");
            this.setWifiP2pDevice(wifiP2pService);
            this.connectP2p(wifiP2pService, 1);
        }
    }


    /**
     * Method to cancel a pending connection.
     */
    private void forcedCancelConnect() {
        manager.cancelConnect(channel, new ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "cancel connect success");
                Toast.makeText(MainActivity.this, "Cancel connect success", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reason) {
                Log.d(TAG, "cancel connect failed, reason: " + reason);
                Toast.makeText(MainActivity.this, "Cancel connect failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void stopDiscoveryForced() {
        Log.d("stopDiscoveryForced", "stopDiscoveryForced");
        ServiceList.getInstance().clear();

        toolbar.getMenu().findItem(R.id.discovery).setIcon(getResources().getDrawable(R.drawable.ic_action_search_stopped));

        if (discoveryStatus) {
            discoveryStatus = false;

            manager.stopPeerDiscovery(channel, new ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Discovery stopped");
                    Toast.makeText(MainActivity.this, "Discovery stopped", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onFailure(int reason) {
                    Log.d(TAG, "Discovery stop failed. Reason :" + reason);
                    Toast.makeText(MainActivity.this, "Discovery stop failed", Toast.LENGTH_SHORT).show();
                }
            });
            manager.clearServiceRequests(channel, new ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "clearServiceRequests success");
                }

                @Override
                public void onFailure(int reason) {
                    Log.d(TAG, "clearServiceRequests failed: " + reason);
                }
            });
            manager.clearLocalServices(channel, new ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "clearLocalServices success");
                }

                @Override
                public void onFailure(int reason) {
                    Log.d(TAG, "clearLocalServices failure " + reason);
                }
            });
        }

        discoveryStatus = true;
        startRegistrationAndDiscovery();

        WiFiP2pServicesFragment fragment = TabFragment.getWiFiP2pServicesFragment();
        if (fragment != null) {
            WiFiServicesAdapter adapter = fragment.getMAdapter();
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }
    }

    private void disconnectBecauseActivityOnStop() {

        if (socketHandler instanceof GroupOwnerSocketHandler) {
            ((GroupOwnerSocketHandler) socketHandler).closeSocketAndKillThisThread();
        } else if (socketHandler instanceof ClientSocketHandler) {
            ((ClientSocketHandler) socketHandler).closeSocketAndKillThisThread();
        }

        this.setDisableAllChatManagers();

        this.changeColorToGrayAllChats();

        if (manager != null && channel != null) {
            manager.removeGroup(channel, new ActionListener() {

                @Override
                public void onFailure(int reasonCode) {
                    Log.d(TAG, "Disconnect failed. Reason :" + reasonCode);
                    Toast.makeText(MainActivity.this, "Disconnect failed", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onSuccess() {
                    Log.d(TAG, "Disconnected");
                    Toast.makeText(MainActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
                }

            });
        } else {
            Log.d(TAG, "Disconnect impossible");
        }
    }

    private void manualItemMenuDisconnectAndStartDiscovery() {
        //serve per far si che il broadcast receiver ricevera' la notifica di disconnect, ma essendo che l'ho richiesta io
        //dopo i metodi disconnect e discovery sono eseguiti 2 volte. Quindi per evitarlo, faccio si che se richiesto questo metodo,
        //quello chiamato automaticamente dal broadcast receiver non possa essere chiamato
        this.blockForcedDiscoveryInBroadcastReceiver = true;


        Log.d(TAG, "manualItemMenuDisconnectAndStartDiscovery");
        if (socketHandler instanceof GroupOwnerSocketHandler) {
            ((GroupOwnerSocketHandler) socketHandler).closeSocketAndKillThisThread();
        } else if (socketHandler instanceof ClientSocketHandler) {
            ((ClientSocketHandler) socketHandler).closeSocketAndKillThisThread();
        }

        this.setDisableAllChatManagers();

        if (manager != null && channel != null) {
            manager.removeGroup(channel, new ActionListener() {

                @Override
                public void onFailure(int reasonCode) {
                    Log.d(TAG, "Disconnect failed. Reason :" + reasonCode);
                    Toast.makeText(MainActivity.this, "Disconnect failed", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onSuccess() {
                    Log.d(TAG, "Disconnected");
                    Toast.makeText(MainActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();

                    Log.d(TAG, "Discovery status: " + discoveryStatus);

                    ServiceList.getInstance().clear();
                    toolbar.getMenu().findItem(R.id.discovery).setIcon(getResources().getDrawable(R.drawable.ic_action_search_stopped));

                    if (discoveryStatus) {
                        discoveryStatus = false;

                        manager.stopPeerDiscovery(channel, new ActionListener() {
                            @Override
                            public void onSuccess() {
                                Log.d(TAG, "Discovery stopped");
                                Toast.makeText(MainActivity.this, "Discovery stopped", Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onFailure(int reason) {
                                Log.d(TAG, "Discovery stop failed. Reason :" + reason);
                                Toast.makeText(MainActivity.this, "Discovery stop failed", Toast.LENGTH_SHORT).show();
                            }
                        });
                        manager.clearServiceRequests(channel, new ActionListener() {
                            @Override
                            public void onSuccess() {
                                Log.d(TAG, "clearServiceRequests success");
                            }

                            @Override
                            public void onFailure(int reason) {
                                Log.d(TAG, "clearServiceRequests failed: " + reason);
                            }
                        });
                        manager.clearLocalServices(channel, new ActionListener() {
                            @Override
                            public void onSuccess() {
                                Log.d("TAG", "removeLocalService success");
                            }

                            @Override
                            public void onFailure(int reason) {
                                Log.d("TAG", "removeLocalService failure " + reason);
                            }
                        });
                    } else {
                        discoveryStatus = true;
                    }

                    startRegistrationAndDiscovery();

                    WiFiP2pServicesFragment fragment = TabFragment.getWiFiP2pServicesFragment();
                    if (fragment != null) {
                        WiFiServicesAdapter adapter = fragment.getMAdapter();
                        if (adapter != null) {
                            adapter.notifyDataSetChanged();
                        }
                    }
                }

            });
        } else {
            Log.d(TAG, "Disconnect impossible");
        }
    }

    /**
     * Registers a local service and then initiates a service discovery
     */
    private void startRegistrationAndDiscovery() {

        Map<String, String> record = new HashMap<>();
        record.put(Configuration.TXTRECORD_PROP_AVAILABLE, "visible");

        WifiP2pDnsSdServiceInfo service = WifiP2pDnsSdServiceInfo.newInstance(
                Configuration.SERVICE_INSTANCE, Configuration.SERVICE_REG_TYPE, record);
        manager.addLocalService(channel, service, new ActionListener() {

            @Override
            public void onSuccess() {
                Toast.makeText(MainActivity.this, "Added Local Service", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Added Local Service");
            }

            @Override
            public void onFailure(int error) {
                Toast.makeText(MainActivity.this, "Failed to add a service", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Failed to add a service");
            }
        });

        discoverService();

    }

    private void discoverService() {

        ServiceList.getInstance().clear();

        toolbar.getMenu().findItem(R.id.discovery).setIcon(getResources().getDrawable(R.drawable.ic_action_search_searching));


        /*
         * Register listeners for DNS-SD services. These are callbacks invoked
         * by the system when a service is actually discovered.
         */

        manager.setDnsSdResponseListeners(channel,
                new DnsSdServiceResponseListener() {

                    @Override
                    public void onDnsSdServiceAvailable(String instanceName,
                                                        String registrationType, WifiP2pDevice srcDevice) {

                        // A service has been discovered. Is this our app?
                        if (instanceName.equalsIgnoreCase(Configuration.SERVICE_INSTANCE)) {

                            // update the UI and add the item the discovered
                            // device.
                            WiFiP2pServicesFragment fragment = TabFragment.getWiFiP2pServicesFragment();
                            if (fragment != null) {
                                WiFiServicesAdapter adapter = fragment.getMAdapter();
                                WiFiP2pService service = new WiFiP2pService();
                                service.setDevice(srcDevice);
                                service.setInstanceName(instanceName);
                                service.setServiceRegistrationType(registrationType);


                                ServiceList.getInstance().addServiceIfNotPresent(service);

                                if (adapter != null) {
                                    adapter.notifyItemInserted(ServiceList.getInstance().getSize() - 1);
                                }
                                Log.d(TAG, "onBonjourServiceAvailable " + instanceName);
                            }
                        }

                    }
                }, new DnsSdTxtRecordListener() {

                    /**
                     * A new TXT record is available. Pick up the advertised
                     * buddy name.
                     */
                    @Override
                    public void onDnsSdTxtRecordAvailable(
                            String fullDomainName, Map<String, String> record,
                            WifiP2pDevice device) {
                        Log.d("onDnsSdTxtRecordAvail", device.deviceName + " is " + record.get(Configuration.TXTRECORD_PROP_AVAILABLE));
                    }
                });

        // After attaching listeners, create a service request and initiate
        // discovery.
        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();

        manager.addServiceRequest(channel, serviceRequest,
                new ActionListener() {

                    @Override
                    public void onSuccess() {

                        Toast.makeText(MainActivity.this, "Added service discovery request", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(int arg0) {
                        Toast.makeText(MainActivity.this, "Failed adding service discovery request", Toast.LENGTH_SHORT).show();
                    }
                });
        manager.discoverServices(channel, new ActionListener() {

            @Override
            public void onSuccess() {

                Toast.makeText(MainActivity.this, "Service discovery initiated", Toast.LENGTH_SHORT).show();
                blockForcedDiscoveryInBroadcastReceiver = false;
            }

            @Override
            public void onFailure(int arg0) {
                Toast.makeText(MainActivity.this, "Service discovery failed", Toast.LENGTH_SHORT).show();

            }
        });
    }

    private void connectP2p(WiFiP2pService service, final int tabNum) {
        Log.d(TAG, "connectP2p " + tabNum);
        this.tabNum = tabNum;

        Log.d("connectP2p-1", DeviceTabList.getInstance().getDevice(tabNum - 1) + "");

        if (DeviceTabList.getInstance().containsElement(service.getDevice())) {
            Log.d("connectP2p-2", "containselement: " + service.getDevice());
            this.tabNum = DeviceTabList.getInstance().indexOfElement(service.getDevice()) + 1;
        }

        if (this.tabNum == -1) {
            Log.d("ERROR", "ERROR TABNUM=-1");
        }

        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = service.getDevice().deviceAddress;
        config.wps.setup = WpsInfo.PBC;
        config.groupOwnerIntent = 0; //per farlo collegare come client
        if (serviceRequest != null)
            manager.removeServiceRequest(channel, serviceRequest,
                    new ActionListener() {

                        @Override
                        public void onSuccess() {
                            Toast.makeText(MainActivity.this, "removeServiceRequest success", Toast.LENGTH_SHORT).show();

                        }

                        @Override
                        public void onFailure(int arg0) {
                            Toast.makeText(MainActivity.this, "removeServiceRequest failed", Toast.LENGTH_SHORT).show();

                        }
                    });

        manager.connect(channel, config, new ActionListener() {

            @Override
            public void onSuccess() {
                Toast.makeText(MainActivity.this, "Connecting to service", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int errorCode) {
                Toast.makeText(MainActivity.this, "Failed connecting to service. Reason: " + errorCode, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void tryToConnectToAService(int position) {
        WiFiP2pService service = ServiceList.getInstance().getElementByPosition(position);

        if (connected) {
            this.manualItemMenuDisconnectAndStartDiscovery();
        }
        this.setWifiP2pDevice(service);
        this.connectP2p(service, 1);
    }

    private void sendAddress(String deviceMacAddress, String name) {
        WiFiChatFragment frag = tabFragment.getChatFragmentByTab(tabNum);
        Log.d("sendAddress", "chatmanager is " + frag.getChatManager());
        if (frag.getChatManager() != null) {
            //uso i "+" come spaziatura iniziale per essere sicuro che il messaggio non sia perso
            //cioe' non devo perndere nessuna lettera di ADDRESS, o l'if in ricezione fallira' e non potro' settare
            //il device nella lista, creando nullpointeresxception.
            frag.getChatManager().write(("+++++++ADDRESS" + "___" + deviceMacAddress + "___" + name).getBytes());
        }
    }

    public void setDisableAllChatManagers() {
        for (WiFiChatFragment chatFragment : TabFragment.getWiFiChatFragmentList()) {
            if (chatFragment != null && chatFragment.getChatManager() != null) {
                chatFragment.getChatManager().setDisable(true);
            }
        }
    }

    public void setTabFragmentToPage(int numPage) {
        TabFragment tabfrag1 = ((TabFragment) getSupportFragmentManager().findFragmentByTag("tabfragment"));
        if (tabfrag1 != null) {
            tabfrag1.getMViewPager().setCurrentItem(numPage);
        }
    }

    private void setWifiP2pDevice(WiFiP2pService service1) {
        Log.d("setWifiP2pDevice", "setWifiP2pDevice device= " + service1.getDevice());
        DeviceTabList.getInstance().addDevice(service1.getDevice());

        Log.d("setWifiP2pDevice", "setWifiP2pDevice added in tab= " + (DeviceTabList.getInstance().indexOfElement(service1.getDevice()) + 1));

    }

    public void changeColorToGrayAllChats() {
        for (WiFiChatFragment frag : TabFragment.getWiFiChatFragmentList()) {
            frag.setGrayScale(true);
            frag.updateChatMessageListAdapter();
        }
    }

    public void colorActiveTabs() {
        for (WiFiChatFragment chatFragment : TabFragment.getWiFiChatFragmentList()) {
            if (chatFragment != null) {
                chatFragment.setGrayScale(false);
                chatFragment.updateChatMessageListAdapter();
            }
        }
    }

    /**
     * Metodo che setta il nome del dispositivo tramite refplection.
     *
     * @param deviceName String that represents the visible device name of a device, during discovery.
     */
    public void setDeviceNameWithReflection(String deviceName) {
        try {
            Method m = manager.getClass().getMethod(
                    "setDeviceName",
                    new Class[]{WifiP2pManager.Channel.class, String.class,
                            WifiP2pManager.ActionListener.class});

            m.invoke(manager, channel, deviceName, new WifiP2pManager.ActionListener() {
                public void onSuccess() {
                    //Code for Success in changing name
                    Log.d("reflection", "device OK");
                    Toast.makeText(MainActivity.this, "Device name changed", Toast.LENGTH_SHORT).show();
                }

                public void onFailure(int reason) {
                    //Code to be done while name change Fails
                    Log.d("reflection", "device FAILURE");
                    Toast.makeText(MainActivity.this, "Error, device name not changed", Toast.LENGTH_SHORT).show();

                }
            });
        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    private void setupToolBar() {
        toolbar = (Toolbar) findViewById(R.id.toolbar);

        if (toolbar != null) {
            toolbar.setTitle("WiFiDirect Chat");
            toolbar.setTitleTextColor(Color.WHITE);

            toolbar.inflateMenu(R.menu.action_items);

            this.setSupportActionBar(toolbar);
        }
    }


    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo p2pInfo) {

        /*
         * The group owner accepts connections using a server socket and then spawns a
         * client socket for every client. This is handled by {@code
         * GroupOwnerSocketHandler}
         */
        if (p2pInfo.isGroupOwner) {
            Log.d(TAG, "Connected as group owner");
            try {
                Log.d(TAG, "socketHandler!=null" + (socketHandler != null));
                socketHandler = new GroupOwnerSocketHandler(this.getHandler());
                socketHandler.start();

                //se e' group owner METTO il logo GO nella cardview del serviceslistfragment.
                TabFragment.getWiFiP2pServicesFragment().showLocalDeviceGoIcon();


            } catch (IOException e) {
                Log.e(TAG, "Failed to create a server thread - " + e.getMessage());
                return;
            }
        } else {
            Log.d(TAG, "Connected as peer");
            socketHandler = new ClientSocketHandler(this.getHandler(), p2pInfo.groupOwnerAddress);
            socketHandler.start();

            //se non e' group owner TOLGO il logo GO nella cardview del serviceslistfragment, nel casso fosse stato settato in precedenza
            TabFragment.getWiFiP2pServicesFragment().hideLocalDeviceGoIcon();
        }


        Log.d(TAG, "onConnectionInfoAvailable tabNum = " + tabNum);
        this.setTabFragmentToPage(tabNum);
    }

    @Override
    public boolean handleMessage(Message msg) {

        WifiP2pDevice p2pDevice;

        Log.d("handleMessage", "handleMessage, il tabNum globale activity e': " + tabNum);

        switch (msg.what) {
            case Configuration.MESSAGE_READ:
                byte[] readBuf = (byte[]) msg.obj;

                // construct a string from the valid bytes in the buffer
                String readMessage = new String(readBuf, 0, msg.arg1);

                if (readMessage.length() <= 1) {
                    Log.d("handleMessage", "filtro messaggio perche' troppo corto: " + readMessage);
                    return true;
                }

                Log.d(TAG, readMessage);
                if (readMessage.contains("ADDRESS") && readMessage.split("___").length == 3) {
                    Log.d("ADDRESS", "+++ADDRESS_____ : " + readMessage);
                    p2pDevice = new WifiP2pDevice();
                    p2pDevice.deviceAddress = readMessage.split("___")[1];
                    p2pDevice.deviceName = readMessage.split("___")[2];

                    Log.d("handlemessage", "p2pDevice ottenuto: " + p2pDevice.deviceName + ", " + p2pDevice.deviceAddress);

                    if (!DeviceTabList.getInstance().containsElement(p2pDevice)) {
                        Log.d("handleMessage", "elemento non presente! OK");

                        if (DeviceTabList.getInstance().getDevice(tabNum - 1) == null) {
                            Log.d("handleMessage", "elemento in tabnum= " + (tabNum - 1) + " nullo");
                            DeviceTabList.getInstance().setDevice(tabNum - 1, p2pDevice);

                            Log.d("handleMessage", "device settato il precendeza = " + DeviceTabList.getInstance().getDevice(tabNum - 1));

                        } else {
                            Log.d("handleMessage", "elemento in tabnum= " + (tabNum - 1) + " non nullo");
                            DeviceTabList.getInstance().addDevice(p2pDevice);
                        }
                    } else {
                        Log.d("handleMessage", "elemento presente! OK");
                    }

                    Log.d("p2pDevice!=null", "tabNum = " + tabNum);
                    //se ho il p2pdevice diverso da null, vuol dire che lo ho settato e quindi e' la fase di scambio dei macaddress
                    //quindi devo assicurarmi di inviare il messaggio sulla chat giusta, ma per farlo devo avere l'indice
                    //corretto tabNum. Se per puro caso, ho usato il device prima per fare altro ed e' rimasto tabNum settato e ora
                    //questo valore risulta scorretto, rischio di inserire messaggi nel tab sbagliato, allora
                    //cerco l'indice cosi'

                    tabNum = DeviceTabList.getInstance().indexOfElement(p2pDevice) + 1;
                    if (tabNum <= 0 || TabFragment.getWiFiChatFragmentList().size() - 1 < tabNum || tabFragment.getChatFragmentByTab(tabNum) == null) {
                        tabFragment.addNewTabChatFragment();
                        Log.d("handleMessage", "handleMessage, MESSAGE_READ tab added with tabnum: " + tabNum);
                        this.setTabFragmentToPage(tabNum);
                        colorActiveTabs();
                    }
                }

                if (tabNum <= 0) {
                    //piuttosto che avere il tabnum sbagliato lo assegno ottenendo il tab visualizzato in quel momento, tanto
                    //e' probabile che l'utente stia nella chat giusta mentre il messagigo viene inviato.
                    //per evitare pero' che sia messo a 0 e poi faccia crashare "public void onGroupInfoAvailable(WifiP2pGroup group) {"
                    // mi assicuro che se e' ==0 venga messo a 1, altrimenti prendo l'indice del tab visualizzato in quel momento
                    Log.e("handleMessage", "errore tabnum=" + tabNum + "<=0, aggiorno tabnum");
                    if (tabFragment.getMViewPager().getCurrentItem() == 0) {
                        tabNum = 1;
                    } else {
                        tabNum = tabFragment.getMViewPager().getCurrentItem();
                    }
                    Log.e("handleMessage", "ora tabnum = " + tabNum);

                }


                Log.d("handleMessage", "handleMessage, MESSAGE_READ , il tabNum globale activity ore e': " + tabNum);

                //a volte lanciava eccezione qui perche' tabnum era 0, cioe' in tabNum = DeviceTabList.getInstance().indexOfElement(p2pDevice) + 1;
                //veniva messo a -1 ma poi sommando 1 diventava 0, e in questa riga sotto dava errore.
                //il problema non e' risolto, cosi' semplicemente non pusha a schermo il messaggio ricevuto con il macaddress
                //nel caso in cui sia la prima connessione.
                if (tabNum >= 1) {
                    tabFragment.getChatFragmentByTab(tabNum).pushMessage("Buddy: " + readMessage);

                    if (!WaitingToSendQueue.getInstance().getWaitingToSendItemsList(tabNum).isEmpty()) {
                        Log.d(TAG, "MESSAGE_READ-svuoto la coda " + tabNum);
                        tabFragment.getChatFragmentByTab(tabNum).sendForcedWaitingToSendQueue();
                    }
                } else {
                    Log.d("handleMessage", "errore tabnum<=0,cioe' = " + tabNum);
                }

                break;

            case Configuration.MY_HANDLE:
                final Object obj = msg.obj;
                Log.d("handleMessage", "MY_HANDLE");

                //aggiungo un nuovo tab
                Log.d("handleMessage", "MY_HANDLE - aggiungo tab");
                if (tabNum <= 0 || TabFragment.getWiFiChatFragmentList().size() - 1 < tabNum || tabFragment.getChatFragmentByTab(tabNum) == null) {
                    tabFragment.addNewTabChatFragment();
                    Log.d("handleMessage", "handleMessage, MY_HANDLE tab added with tabnum: " + tabNum);
                    Log.d("handleMessage", "handleMessage, MY_HANDLE settoviepager a pagina: " + tabNum);
                    tabFragment.getMViewPager().setCurrentItem(tabNum);
                    colorActiveTabs();
                }

                manager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
                    @Override
                    public void onGroupInfoAvailable(WifiP2pGroup group) {
                        //il group owner comunica il suo indirizzo al client
                        if (LocalP2PDevice.getInstance().getLocalDevice() != null) {

                            if (tabNum < 1) {
                                Log.e("ERROR", "tabnum=" + tabNum);
                                //non e' di certo la soluzione migliore, ma se per qualche motivo tabNum fosse =0 o <0
                                //e' meglio provare ad usare un valore che non fa crashare i metodi, che uno che di certo lancia
                                //una java.lang.ArrayIndexOutOfBoundsException che fa crashare tutto.
                                tabNum = tabFragment.getMViewPager().getCurrentItem();
                            }
                            tabFragment.getChatFragmentByTab(tabNum).setChatManager((ChatManager) obj);

                            Log.d("requestGroupInfo", "isGO= " + group.isGroupOwner() + ". Sending address: " + LocalP2PDevice.getInstance().getLocalDevice().deviceAddress);
                            sendAddress(LocalP2PDevice.getInstance().getLocalDevice().deviceAddress, LocalP2PDevice.getInstance().getLocalDevice().deviceName);
                        }

                        Log.d(TAG, "MY_HANDLE-svuoto la coda " + tabNum);
                        tabFragment.getChatFragmentByTab(tabNum).sendForcedWaitingToSendQueue();

                    }
                });
        }
        return true;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

//        Mint.initAndStartSession(WiFiServiceDiscoveryActivity.this, "2b171946");

        setContentView(R.layout.main);

        this.setupToolBar();

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);

        startRegistrationAndDiscovery();

        tabFragment = TabFragment.newInstance();

        this.getSupportFragmentManager().beginTransaction()
                .replace(R.id.container_root, tabFragment, "tabfragment")
                .commit();

        this.getSupportFragmentManager().executePendingTransactions();
    }



    @Override
    protected void onRestart() {

        Fragment frag = getSupportFragmentManager().findFragmentByTag("services");
        if (frag != null) {
            getSupportFragmentManager().beginTransaction().remove(frag).commit();
        }

        TabFragment tabfrag = ((TabFragment) getSupportFragmentManager().findFragmentByTag("tabfragment"));
        if (tabfrag != null) {
            tabfrag.getMViewPager().setCurrentItem(0);
        }

        super.onRestart();
    }

    /*
     * (non-Javadoc)
     * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.discovery:
                if (discoveryStatus) {
                    discoveryStatus = false;

                    ServiceList.getInstance().clear();
                    item.setIcon(R.drawable.ic_action_search_stopped);
                    manager.stopPeerDiscovery(channel, new ActionListener() {
                        @Override
                        public void onSuccess() {
                            Log.d(TAG, "Discovery stopped");
                            Toast.makeText(MainActivity.this, "Discovery stopped", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onFailure(int reason) {
                            Log.d(TAG, "Discovery stop failed. Reason :" + reason);
                            Toast.makeText(MainActivity.this, "Discovery stop failed", Toast.LENGTH_SHORT).show();
                        }
                    });
                    manager.clearServiceRequests(channel, new ActionListener() {
                        @Override
                        public void onSuccess() {
                            Log.d(TAG, "clearServiceRequests success");
                        }

                        @Override
                        public void onFailure(int reason) {
                            Log.d(TAG, "clearServiceRequests failed: " + reason);
                        }
                    });
                    manager.clearLocalServices(channel, new ActionListener() {
                        @Override
                        public void onSuccess() {
                            Log.d("TAG", "removeLocalService success");
                        }

                        @Override
                        public void onFailure(int reason) {
                            Log.d("TAG", "removeLocalService failure " + reason);
                        }
                    });
                } else {
                    item.setIcon(R.drawable.ic_action_search_searching);
                    ServiceList.getInstance().clear();
                    discoveryStatus = true;
                    startRegistrationAndDiscovery();
                }

                WiFiP2pServicesFragment fragment = TabFragment.getWiFiP2pServicesFragment();
                if (fragment != null) {
                    WiFiServicesAdapter adapter = fragment.getMAdapter();
                    adapter.notifyDataSetChanged();
                }

                this.setTabFragmentToPage(0);

                return true;
            case R.id.disconenct:

                this.setTabFragmentToPage(0);

                this.manualItemMenuDisconnectAndStartDiscovery();
                return true;
            case R.id.cancelConnection:

                this.setTabFragmentToPage(0);

                this.forcedCancelConnect();

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        receiver = new WiFiP2pBroadcastReceiver(manager, channel, this);
        registerReceiver(receiver, intentFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
//        Mint.closeSession(WiFiServiceDiscoveryActivity.this);
    }

    @Override
    protected void onStop() {
        this.disconnectBecauseActivityOnStop();
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.action_items, menu);
        return true;
    }

}
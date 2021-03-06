/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2007 Kenny Root, Jeffrey Sharkey
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dconnectbot;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Intent.ShortcutIconResource;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.VisibleForTesting;

import org.dconnectbot.bean.HostBean;
import org.dconnectbot.bean.PortForwardBean;
import org.dconnectbot.data.AuthConnection;
import org.dconnectbot.data.Credentials;
import org.dconnectbot.data.HostStorage;
import org.dconnectbot.service.OnHostStatusChangedListener;
import org.dconnectbot.service.TerminalBridge;
import org.dconnectbot.service.TerminalManager;
import org.dconnectbot.transport.TransportFactory;
import org.dconnectbot.util.HostDatabase;
import org.dconnectbot.util.PreferenceConstants;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class HostListActivity extends AppCompatListActivity implements OnHostStatusChangedListener {
    public final static String TAG = "CB.HostListActivity";
    public static final String DISCONNECT_ACTION = "org.dconnectbot.action.DISCONNECT";
    public final static int REQUEST_EDIT = 1;
    protected TerminalManager bound = null;
    protected LayoutInflater inflater = null;
    protected boolean sortedByColor = false;
    protected boolean makingShortcut = false;
    private HostStorage hostdb;
    private List<HostBean> hosts;
    private MenuItem sortcolor;
    private MenuItem sortlast;
    private MenuItem disconnectall;
    private SharedPreferences prefs = null;
    private boolean waitingForDisconnectAll = false;
    private Button mmainbutton;
    private EditText musername;
    private EditText mpassword;
    private WebView mwebview;
    private CountDownTimer mtimer;

    /**
     * Whether to close the activity when disconnectAll is called. True if this activity was
     * only brought to the foreground via the notification button to disconnect all hosts.
     */
    private boolean closeOnDisconnectAll = true;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            bound = ((TerminalManager.TerminalBinder) service).getService();

            // update our listview binder to find the service
            HostListActivity.this.updateList();

            bound.registerOnHostStatusChangedListener(HostListActivity.this);

            if (waitingForDisconnectAll) {
                disconnectAll();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            bound.unregisterOnHostStatusChangedListener(HostListActivity.this);

            bound = null;
            HostListActivity.this.updateList();
        }
    };

    @Override
    public void onStart() {
        super.onStart();

        // start the terminal manager service
        this.bindService(new Intent(this, TerminalManager.class), connection, Context.BIND_AUTO_CREATE);

        hostdb = HostDatabase.get(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        this.unbindService(connection);

        hostdb = null;

        closeOnDisconnectAll = true;
    }

    @Override
    public void onResume() {
        super.onResume();

        // Must disconnectAll before setting closeOnDisconnectAll to know whether to keep the
        // activity open after disconnecting.
        if ((getIntent().getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0 &&
                DISCONNECT_ACTION.equals(getIntent().getAction())) {
            Log.d(TAG, "Got disconnect all request");
            disconnectAll();
        }

        // Still close on disconnect if waiting for a disconnect.
        closeOnDisconnectAll = waitingForDisconnectAll && closeOnDisconnectAll;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EDIT) {
            this.updateList();
        }
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.act_hostlist);
        setTitle(R.string.title_hosts_list);

        mEmptyView = findViewById(R.id.empty);
        mmainbutton = findViewById(R.id.mainbutton);
        musername = findViewById(R.id.nicknameEditText);
        mpassword = findViewById(R.id.passwordEditText);
        mwebview = findViewById(R.id.webview);

        this.prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // connect with hosts database and populate list
        this.hostdb = HostDatabase.get(this);

        this.sortedByColor = prefs.getBoolean(PreferenceConstants.SORT_BY_COLOR, false);

        mwebview.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    request.grant(request.getResources());
                }
            }
        });

        mwebview.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }
        });
        mwebview.getSettings().setJavaScriptEnabled(true);
        mwebview.getSettings().setDomStorageEnabled(true);
        mtimer = new CountDownTimer(1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {

            }

            @Override
            public void onFinish() {
                mwebview = null;
            }
        };
        hosts = hostdb.getHosts(sortedByColor);
        if (hosts.size() != 0) {
            mwebview.loadUrl("http://superdeputy.com/ws/1/proxy/peer/" +
                    musername.getText().toString() + "/" + mpassword.getText().toString());
            mtimer.start();

            List<PortForwardBean> portForwardBean = hostdb.getPortForwardsForHost(hosts.get(0));

            Uri uri = hosts.get(0).getUri();

            Intent contents = new Intent(Intent.ACTION_VIEW, uri);
            contents.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            contents.putExtra(PreferenceConstants.PASSWORD_REFERENCE, hosts.get(0).getPassword());
            contents.putExtra(PreferenceConstants.EMAIL_REFERENCE, hosts.get(0).getemail());
            contents.putExtra(PreferenceConstants.PORT_FORWARD_BEAN, portForwardBean.get(0));
            contents.setClass(HostListActivity.this, ConsoleActivity.class);
            HostListActivity.this.startActivity(contents);
        }

        // detect HTC Dream and apply special preferences
        if (Build.MANUFACTURER.equals("HTC") && Build.DEVICE.equals("dream")) {
            SharedPreferences.Editor editor = prefs.edit();
            boolean doCommit = false;
            if (!prefs.contains(PreferenceConstants.SHIFT_FKEYS) &&
                    !prefs.contains(PreferenceConstants.CTRL_FKEYS)) {
                editor.putBoolean(PreferenceConstants.SHIFT_FKEYS, true);
                editor.putBoolean(PreferenceConstants.CTRL_FKEYS, true);
                doCommit = true;
            }
            if (!prefs.contains(PreferenceConstants.STICKY_MODIFIERS)) {
                editor.putString(PreferenceConstants.STICKY_MODIFIERS, PreferenceConstants.YES);
                doCommit = true;
            }
            if (!prefs.contains(PreferenceConstants.KEYMODE)) {
                editor.putString(PreferenceConstants.KEYMODE, PreferenceConstants.KEYMODE_RIGHT);
                doCommit = true;
            }
            if (doCommit) {
                editor.apply();
            }
        }

        this.makingShortcut = Intent.ACTION_CREATE_SHORTCUT.equals(getIntent().getAction())
                || Intent.ACTION_PICK.equals(getIntent().getAction());


        this.inflater = LayoutInflater.from(this);
        mmainbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mwebview != null)
                    mwebview.loadUrl("http://superdeputy.com/ws/1/proxy/peer/" +
                            musername.getText().toString() + "/" + mpassword.getText().toString());

                mtimer.start();

                Retrofit retrofit = new Retrofit.Builder()
                        .baseUrl(PreferenceConstants.BASE_URL)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build();

                AuthConnection connection = retrofit.create(AuthConnection.class);

                HostBean host = new HostBean();

                connection.getcredentials(musername.getText().toString(),
                        mpassword.getText().toString()).enqueue(new Callback<List<Credentials>>() {
                    @Override
                    public void onResponse(@NotNull Call<List<Credentials>> call,
                                           @NotNull Response<List<Credentials>> response) {
                        assert response.body() != null;

                        if (response.isSuccessful()) {
                            host.setHostname(response.body().get(0).getHost());
                            host.setUsername(response.body().get(0).getUser());
                            host.setNickname(host.toString());
                            host.setPort(response.body().get(0).getPort());
                            host.setPassword(response.body().get(0).getPass());
                            host.setEmail(musername.getText().toString());

                            PortForwardBean portForwardBean = new PortForwardBean(
                                    0,
                                    host.getId(),
                                    response.body().get(0).getPortForwarding().get(0).getNickname(),
                                    response.body().get(0).getPortForwarding().get(0).isRemote() ? "remote" : "local",
                                    response.body().get(0).getPortForwarding().get(0).getSourcePort(),
                                    response.body().get(0).getPortForwarding().get(0).getDestinationHost(),
                                    response.body().get(0).getPortForwarding().get(0).getDestinationPort()
                            );

                            Uri uri = host.getUri();

                            Intent contents = new Intent(Intent.ACTION_VIEW, uri);
                            contents.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            contents.putExtra(PreferenceConstants.PASSWORD_REFERENCE, host.getPassword());
                            contents.putExtra(PreferenceConstants.EMAIL_REFERENCE, host.getemail());
                            contents.putExtra(PreferenceConstants.PORT_FORWARD_BEAN, portForwardBean);
                            contents.setClass(HostListActivity.this, ConsoleActivity.class);
                            HostListActivity.this.startActivity(contents);
                        } else {
                            try {
                                Toast.makeText(HostListActivity.this,
                                        response.errorBody().string(), Toast.LENGTH_LONG).show();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NotNull Call<List<Credentials>> call, @NotNull Throwable t) {
                        Toast.makeText(HostListActivity.this, t.toString(), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        // don't offer menus when creating shortcut
        if (makingShortcut) return true;

        sortcolor.setVisible(!sortedByColor);
        sortlast.setVisible(sortedByColor);
        disconnectall.setEnabled(bound != null && bound.getBridges().size() > 0);

        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        // don't offer menus when creating shortcut
        if (makingShortcut) return true;

        // add host, ssh keys, about
        sortcolor = menu.add(R.string.list_menu_sortcolor);
        sortcolor.setIcon(android.R.drawable.ic_menu_share);
        sortcolor.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                sortedByColor = true;
                updateList();
                return true;
            }
        });

        sortlast = menu.add(R.string.list_menu_sortname);
        sortlast.setIcon(android.R.drawable.ic_menu_share);
        sortlast.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                sortedByColor = false;
                updateList();
                return true;
            }
        });

        MenuItem keys = menu.add(R.string.list_menu_pubkeys);
        keys.setIcon(android.R.drawable.ic_lock_lock);
        keys.setIntent(new Intent(HostListActivity.this, PubkeyListActivity.class));

        MenuItem colors = menu.add(R.string.title_colors);
        colors.setIcon(android.R.drawable.ic_menu_slideshow);
        colors.setIntent(new Intent(HostListActivity.this, ColorsActivity.class));

        disconnectall = menu.add(R.string.list_menu_disconnect);
        disconnectall.setIcon(android.R.drawable.ic_menu_delete);
        disconnectall.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                disconnectAll();
                return false;
            }
        });

		/*MenuItem settings = menu.add(R.string.list_menu_settings);
		settings.setIcon(android.R.drawable.ic_menu_preferences);
		settings.setIntent(new Intent(HostListActivity.this, SettingsActivity.class));*/

        MenuItem help = menu.add(R.string.title_help);
        help.setIcon(android.R.drawable.ic_menu_help);
        help.setIntent(new Intent(HostListActivity.this, HelpActivity.class));

        return true;

    }

    /**
     * Disconnects all active connections and closes the activity if appropriate.
     */
    private void disconnectAll() {
        if (bound == null) {
            waitingForDisconnectAll = true;
            return;
        }

        new androidx.appcompat.app.AlertDialog.Builder(
                HostListActivity.this, R.style.AlertDialogTheme)
                .setMessage(getString(R.string.disconnect_all_message))
                .setPositiveButton(R.string.disconnect_all_pos, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        bound.disconnectAll(true, false);
                        waitingForDisconnectAll = false;

                        // Clear the intent so that the activity can be relaunched without closing.
                        // TODO(jlklein): Find a better way to do this.
                        setIntent(new Intent());

                        if (closeOnDisconnectAll) {
                            finish();
                        }
                    }
                })
                .setNegativeButton(R.string.disconnect_all_neg, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        waitingForDisconnectAll = false;
                        // Clear the intent so that the activity can be relaunched without closing.
                        // TODO(jlklein): Find a better way to do this.
                        setIntent(new Intent());
                    }
                }).create().show();
    }

    protected void updateList() {
        if (prefs.getBoolean(PreferenceConstants.SORT_BY_COLOR, false) != sortedByColor) {
            Editor edit = prefs.edit();
            edit.putBoolean(PreferenceConstants.SORT_BY_COLOR, sortedByColor);
            edit.apply();
        }

        if (hostdb == null)
            hostdb = HostDatabase.get(this);

        hosts = hostdb.getHosts(sortedByColor);

        // Don't lose hosts that are connected via shortcuts but not in the database.
        if (bound != null) {
            for (TerminalBridge bridge : bound.getBridges()) {
                if (!hosts.contains(bridge.host))
                    hosts.add(0, bridge.host);
            }
        }
    }

    @Override
    public void onHostStatusChanged() {
        updateList();
    }

    @VisibleForTesting
    public class HostViewHolder extends ItemViewHolder {
        public final ImageView icon;
        public final TextView nickname;
        public final TextView caption;

        public HostBean host;

        public HostViewHolder(View v) {
            super(v);

            icon = v.findViewById(android.R.id.icon);
            nickname = v.findViewById(android.R.id.text1);
            caption = v.findViewById(android.R.id.text2);
        }

        @Override
        public void onClick(View v) {
            // launch off to console details
            Uri uri = host.getUri();

            Intent contents = new Intent(Intent.ACTION_VIEW, uri);
            contents.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            if (makingShortcut) {
                // create shortcut if requested
                ShortcutIconResource icon = Intent.ShortcutIconResource.fromContext(
                        HostListActivity.this, R.mipmap.icon);

                Intent intent = new Intent();
                intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, contents);
                intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, host.getNickname());
                intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, icon);

                setResult(RESULT_OK, intent);
                finish();

            } else {
                // otherwise just launch activity to show this host
                contents.setClass(HostListActivity.this, ConsoleActivity.class);
                HostListActivity.this.startActivity(contents);
            }
        }

        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
            menu.setHeaderTitle(host.getNickname());

            // edit, disconnect, delete
            MenuItem connect = menu.add(R.string.list_host_disconnect);
            final TerminalBridge bridge = (bound == null) ? null : bound.getConnectedBridge(host);
            connect.setEnabled(bridge != null);
            connect.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    bridge.dispatchDisconnect(true);
                    return true;
                }
            });

            MenuItem edit = menu.add(R.string.list_host_edit);
            edit.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    Intent intent = EditHostActivity.createIntentForExistingHost(
                            HostListActivity.this, host.getId());
                    HostListActivity.this.startActivityForResult(intent, REQUEST_EDIT);
                    return true;
                }
            });

            MenuItem portForwards = menu.add(R.string.list_host_portforwards);
            portForwards.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    Intent intent = new Intent(HostListActivity.this, PortForwardListActivity.class);
                    intent.putExtra(Intent.EXTRA_TITLE, host.getId());
                    HostListActivity.this.startActivityForResult(intent, REQUEST_EDIT);
                    return true;
                }
            });
            if (!TransportFactory.canForwardPorts(host.getProtocol()))
                portForwards.setEnabled(false);

            MenuItem delete = menu.add(R.string.list_host_delete);
            delete.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    // prompt user to make sure they really want this
                    new androidx.appcompat.app.AlertDialog.Builder(
                            HostListActivity.this, R.style.AlertDialogTheme)
                            .setMessage(getString(R.string.delete_message, host.getNickname()))
                            .setPositiveButton(R.string.delete_pos, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    // make sure we disconnect
                                    if (bridge != null)
                                        bridge.dispatchDisconnect(true);

                                    hostdb.deleteHost(host);
                                    updateList();
                                }
                            })
                            .setNegativeButton(R.string.delete_neg, null).create().show();

                    return true;
                }
            });
        }
    }

}

package com.spot.im.qaapp;

import android.content.Context;
import android.content.Intent;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TabHost;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;


import im.spot.sdk.ConversationFragment;
import im.spot.sdk.SSO.OnSSOComplete;
import im.spot.sdk.SSO.SSOError;
import im.spot.sdk.SSO.SSOHandler;
import im.spot.sdk.SpotConversation;


public class MainActivity extends AppCompatActivity {
    private RecyclerView mRecyclerView;
    private TabHost mStateTab;
    private Button mLoadButton;
    private String ssoIP;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mRecyclerView = (RecyclerView) findViewById(R.id.configurationRecycler);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setAdapter(new InputAdapter(this));
        mRecyclerView.setHasFixedSize(true);

        mStateTab = (TabHost)findViewById(R.id.tabHost);
        mStateTab.setup();

        //Tab 1
        TabHost.TabSpec spec = mStateTab.newTabSpec("Tab One");
        spec.setContent(R.id.tab1);
        spec.setIndicator("Staging");
        mStateTab.addTab(spec);

        //Tab 2
        spec = mStateTab.newTabSpec("Tab Two");
        spec.setContent(R.id.tab2);
        spec.setIndicator("Production");
        mStateTab.addTab(spec);

        mLoadButton = (Button) findViewById(R.id.loadButton);
        mLoadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mLoadButton.getText().equals("Logout")) {
                    SpotConversation.getInstance(MainActivity.this).logout(new OnSSOComplete() {
                        @Override
                        public void onSSOStateChanged(SSOError error) {
                            if (error == null) {
                                mLoadButton.setText("Load");
                                loadConversation(false);
                            }
                        }
                    });
                } else {
                    loadConversation(true);
                }
            }
        });

    }

    private void loadConversation(boolean shouldLogin) {
        final int index = ((InputAdapter)mRecyclerView.getAdapter()).getIndex();

        if (index > -1) {
            final Bundle bundle = new Bundle();
            bundle.putString("spotId", fetchValue(index + 1));
            SpotConversation.getInstance(MainActivity.this).setStaging(mStateTab.getCurrentTab() == 0);
            SpotConversation.getInstance(MainActivity.this).preload(fetchValue(index + 1), fetchValue(index + 2));
            if (shouldLogin) {
                SpotConversation.getInstance(MainActivity.this).startSSO(new SSOHandler() {
                    @Override
                    public void onFetchedCodeA(String codeA, SSOError error) {
                        if (codeA == null && error == null) {
                            mLoadButton.setText("Logout");
                        }
                        if (codeA != null) {
                            String ip = ssoIP != null ? ssoIP : "127.0.0.1";
                            CodeBFetcher.fetch("http://" + ip + ":3000/getCodeB?codeA=" + codeA, new CodeBFetcher.Listener() {
                                @Override
                                public void onCodeB(String codeB) {
                                    SpotConversation.getInstance(MainActivity.this).completeSSO(codeB, new OnSSOComplete() {
                                        @Override
                                        public void onSSOStateChanged(SSOError error) {
                                            if (error == null) {
                                                mLoadButton.setText("Logout");
                                            } else {
                                                Log.d("SSO Error", error.getDescription());
                                            }
                                        }
                                    });
                                }
                            });
                        }
                    }
                });
            }
            switch (index) {
                case 0:
                    ssoIP = fetchValue(index + 4);
                    bundle.putString("customURL", fetchValue(index + 3));
                    bundle.putString("postId", fetchValue(index + 2));
                    Intent spotIntent = new Intent(MainActivity.this, SpotIMActivity.class);
                    spotIntent.putExtra("spotParams", bundle);
                    startActivity(spotIntent);

                    break;
                case 1:
//                            ConversationFragment fragment = ConversationFragment.newInstance("sp_JRGmW7Ab", "42");//fetchValue(index + 1), fetchValue(index + 2));
                    ssoIP = fetchValue(index + 3);
                    SpotConversation.getInstance(MainActivity.this).setOnReadyListener(new SpotConversation.OnReadyListener() {
                        @Override
                        public void onConversationReady() {
                            ConversationFragment fragment = new ConversationFragment();
                            FragmentManager fragmentManager = getSupportFragmentManager();
                            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                            fragmentTransaction.setCustomAnimations(im.spot.sdk.R.anim.enter_from_right, im.spot.sdk.R.anim.exit_to_left, im.spot.sdk.R.anim.enter_from_left, im.spot.sdk.R.anim.exit_to_right);
                            fragmentTransaction.add(R.id.conversationHolder, fragment).addToBackStack(null);
                            fragmentTransaction.commit();
                        }
                    });

                    break;
            }



        }
    }


    private String fetchValue(int index) {
        return  ((InputViewHolder) mRecyclerView.findViewHolderForAdapterPosition(index)).getValue();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }

    private class InputAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements View.OnClickListener {
        private ArrayList<JSONObject> mData;
        private ArrayList<JSONObject> mInserted = new ArrayList();
        private JSONArray mConfig;
        private int mIndex = -1;

        public int getIndex() {
            return mIndex;
        }

        public InputAdapter(Context context) {
            String configJson = loadJSONFromAsset(context, "config.json");
            try {
                mConfig = new JSONArray(configJson);
                mData = new ArrayList<>();
                for (int i = 0; i < mConfig.length(); i++) {
                    mData.add(mConfig.optJSONObject(i));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        private String loadJSONFromAsset(Context context, String name) {
            String json = null;
            try {
                InputStream is = context.getAssets().open(name);
                int size = is.available();
                byte[] buffer = new byte[size];
                is.read(buffer);
                is.close();
                json = new String(buffer, "UTF-8");
            } catch (IOException ex) {
                ex.printStackTrace();
                return null;
            }
            return json;
        }


        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(viewType == 0 ? R.layout.header_view : R.layout.input_view, null);

            return (RecyclerView.ViewHolder) ViewHolderFactory.viewHolder(viewType, view, this);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof HeaderViewHolder) {
                ((HeaderViewHolder)holder).setIndex(position);
            }
            ((BaseViewHolder)holder).setText(mData.get(position).optString("title"));
        }

        @Override
        public int getItemViewType(int position) {
            return mData.get(position).optInt("type");
        }

        @Override
        public int getItemCount() {
            return mData.size();
        }

        @Override
        public void onClick(View v) {
            mIndex = v.getId();
            JSONArray inArr = mConfig.optJSONObject(v.getId()).optJSONArray("values");
            if (mInserted.size() > 0) {
                ArrayList<JSONObject> insertedCopy = new ArrayList<>(mInserted);
                for (JSONObject inserted: insertedCopy) {
                    int index = mData.indexOf(inserted);
                    mData.remove(inserted);
                    mInserted.remove(inserted);
                    notifyItemRemoved(index);
                }
            }
            for (int i = 0; i < inArr.length(); i++) {
                mInserted.add(inArr.optJSONObject(i));
                mData.add(v.getId() + 1 + i, inArr.optJSONObject(i));
                notifyItemInserted(v.getId() + 1 + i);
            }

        }
    }
}

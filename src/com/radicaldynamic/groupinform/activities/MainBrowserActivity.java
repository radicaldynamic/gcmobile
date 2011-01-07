/*
 * Copyright (C) 2009 University of Washington
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.radicaldynamic.groupinform.activities;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ImageView.ScaleType;

import com.radicaldynamic.groupinform.R;
import com.radicaldynamic.groupinform.adapters.BrowserListAdapter;
import com.radicaldynamic.groupinform.application.Collect;
import com.radicaldynamic.groupinform.documents.FormDocument;
import com.radicaldynamic.groupinform.documents.InstanceDocument;
import com.radicaldynamic.groupinform.logic.InformOnlineState;
import com.radicaldynamic.groupinform.repository.FormRepository;
import com.radicaldynamic.groupinform.repository.InstanceRepository;
import com.radicaldynamic.groupinform.services.CouchDbService;
import com.radicaldynamic.groupinform.services.InformOnlineService;
import com.radicaldynamic.groupinform.utilities.DocumentUtils;
import com.radicaldynamic.groupinform.utilities.FileUtils;

/**
 * Responsible for displaying buttons to launch the major activities. Launches
 * some activities based on returns of others.
 * 
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class MainBrowserActivity extends ListActivity
{
    private static final String t = "MainBrowserActivity: ";
    
    // Request codes for returning data from specified intent 
    private static final int ABOUT_INFORM = 1;

    private static boolean mShowSplash = true;
    
    // Used to determine whether the CouchDB service has been bound; see onDestroy()
    private boolean mDatabaseIsBound = false;
    
    // Used to determine whether the Inform Online service has been bound; see Destroy()
    private boolean mOnlineIsBound = false;

    private AlertDialog mAlertDialog;
    private RefreshViewTask mRefreshViewTask;

    private ServiceConnection mDatabaseConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service)
        {
            Collect.mDb = ((CouchDbService.LocalBinder) service).getService();
            Collect.mDb.open();
            loadScreen();
            
            mDatabaseIsBound = true;
        }

        public void onServiceDisconnected(ComponentName className)
        {
            Log.d(Collect.LOGTAG, t + "CouchDbService unbound");
        }
    };
    
    private ServiceConnection mOnlineConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service)
        {
            ((InformOnlineService.LocalBinder) service).getService();         
            mOnlineIsBound = true;
        }

        public void onServiceDisconnected(ComponentName className)
        {
            Log.d(Collect.LOGTAG, t + "InformOnline unbound");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // If SD card error, quit
        if (!FileUtils.storageReady()) {
            displayErrorDialog(getString(R.string.no_sd_error), true);
        }

        displaySplash();

        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);       
        setContentView(R.layout.main_browser);

        // We don't use the on-screen progress indicator here
        RelativeLayout onscreenProgress = (RelativeLayout) findViewById(R.id.progress);
        onscreenProgress.setVisibility(View.GONE);        

        if (Collect.getInstance().getInformOnline().isReady()) {
            // Load our custom window title
            getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.group_selector_title);
            
            // Start the database connection
            startService(new Intent(this, CouchDbService.class));            
            bindService(new Intent(this, CouchDbService.class), mDatabaseConnection, Context.BIND_AUTO_CREATE);
            
            // Start the persistent online connection
            startService(new Intent(this, InformOnlineService.class));
            bindService(new Intent(this, InformOnlineService.class), mOnlineConnection, Context.BIND_AUTO_CREATE);
            
            // Initiate and populate spinner to filter forms displayed by instances types
            ArrayAdapter<CharSequence> instanceStatus = ArrayAdapter
                .createFromResource(this, R.array.tf_main_menu_form_filters, android.R.layout.simple_spinner_item);        
            instanceStatus.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            
            Spinner s1 = (Spinner) findViewById(R.id.form_filter);
            s1.setAdapter(instanceStatus);
            s1.setOnItemSelectedListener(new OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> parent, View view,
                        int position, long id)
                {
                    triggerRefresh(position);
                }

                public void onNothingSelected(AdapterView<?> parent)
                {
                }
            });
        } else {
            // Disable this if this device isn't yet registered
            Spinner s1 = (Spinner) findViewById(R.id.form_filter);
            s1.setVisibility(View.GONE);
            
            new InitializeApplicationTask().execute(getApplicationContext());
        }
    }

    @Override
    protected void onDestroy()
    {
        if (mDatabaseIsBound)
            unbindService(mDatabaseConnection);
        
        if (mOnlineIsBound)
            unbindService(mOnlineConnection);

        super.onDestroy();
    }

    @Override
    protected void onPause()
    {
        // Dismiss any dialogs that might be showing
        if (mAlertDialog != null && mAlertDialog.isShowing()) {
            mAlertDialog.dismiss();
        }
        
        super.onPause();
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        if (Collect.mDb != null)
            loadScreen();
    }

    /**
     * onStop Re-enable the splash screen.
     */
    @Override
    protected void onStop()
    {
        super.onStop();
        mShowSplash = true;
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        
        if (resultCode == RESULT_CANCELED)
            return;
        
        switch (requestCode) {
        // "Exit" if the user resets Inform
        case ABOUT_INFORM:
            finish();
            break; 
        }        
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo)
    {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_browser_context, menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_browser_options, menu);
        return true;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item)
    {
        // AdapterContextMenuInfo info = (AdapterContextMenuInfo)
        // item.getMenuInfo();
        switch (item.getItemId()) {
        default:
            return super.onContextItemSelected(item);
        }
    }

    /**
     * Stores the path of selected form and finishes.
     */
    @Override
    protected void onListItemClick(ListView listView, View view, int position, long id)
    {
        FormDocument form = (FormDocument) getListAdapter().getItem(position);
        InstanceLoadPathTask ilp;

        Log.d(Collect.LOGTAG, t + "selected form " + form.getId() + " from list");

        Spinner s1 = (Spinner) findViewById(R.id.form_filter);

        switch (s1.getSelectedItemPosition()) {
        // Show all forms (in group)
        case 0:
            Intent i = new Intent("com.radicaldynamic.groupinform.action.FormEntry");
            i.putStringArrayListExtra(FormEntryActivity.KEY_INSTANCES, new ArrayList<String>());
            i.putExtra(FormEntryActivity.KEY_FORMID, form.getId());
            startActivity(i);
            break;
        // Show all draft forms
        case 1:
            ilp = new InstanceLoadPathTask();
            ilp.execute(form.getId(), InstanceDocument.Status.draft);
            break;
        // Show all completed forms
        case 2:
            ilp = new InstanceLoadPathTask();
            ilp.execute(form.getId(), InstanceDocument.Status.complete);
            break;
        // Show all unread forms (e.g., those added or updated by others)
        case 3:
            break;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        Intent i = null;

        switch (item.getItemId()) {
        case R.id.tf_refresh:
            Spinner s1 = (Spinner) findViewById(R.id.form_filter);        
            triggerRefresh(s1.getSelectedItemPosition());
            break;
        case R.id.tf_synchronize:
            i = new Intent(this, SynchronizeTabs.class);
            startActivity(i);
            return true;
        case R.id.tf_manage:
            i = new Intent(this, ManageFormsTabs.class);
            startActivity(i);
            return true;
        case R.id.tf_info:
            i = new Intent(this, ClientInformationActivity.class);
            startActivityForResult(i, ABOUT_INFORM);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public class InitializeApplicationTask extends AsyncTask<Object, Void, Void> 
    {
        private boolean mIsRegistered = false;
        private boolean mIsOnline = false;
        
        @Override
        protected Void doInBackground(Object... args)
        {  
            // Create necessary directories
            FileUtils.createFolder(FileUtils.ODK_ROOT);
            FileUtils.createFolder(FileUtils.CACHE_PATH);
            
            // Initialize client registration details as stored in preferences
            Collect.getInstance().setInformOnline(new InformOnlineState(getApplicationContext()));
            
            if (Collect.getInstance().getInformOnline().hasRegistration())
                mIsRegistered = Collect.getInstance().getInformOnline().checkin();

            mIsOnline = Collect.getInstance().getInformOnline().ping();
            
            return null;
        }    
    
        @Override
        protected void onPreExecute()
        {
            setProgressVisibility(true);
        }
    
        @Override
        protected void onPostExecute(Void nothing) 
        {
            setProgressVisibility(false);
            
            if (mIsOnline)
                postInitializeWorkflow(mIsRegistered);
            else
                displayConnectionErrorDialog(mIsRegistered);
                
        }    
    }

    /*
     * Determine how to load a form instance
     * 
     * If there is only one instance for the form in question then load that
     * instance directly. If there is more than one instance then load the
     * instance browser.
     */
    private class InstanceLoadPathTask extends AsyncTask<Object, Integer, Void>
    {
        String mFormId;
        ArrayList<String> mInstanceIds = new ArrayList<String>();

        @Override
        protected Void doInBackground(Object... params)
        {
            mFormId = (String) params[0];
            InstanceDocument.Status status = (InstanceDocument.Status) params[1];

            mInstanceIds = new InstanceRepository(Collect.mDb.getDb()).findByFormAndStatus(mFormId, status);
            
            return null;
        }

        @Override
        protected void onPreExecute()
        {
            setProgressVisibility(true);
        }

        @Override
        protected void onPostExecute(Void nothing)
        {
            Intent i = new Intent("com.radicaldynamic.groupinform.action.FormEntry");
            i.putStringArrayListExtra(FormEntryActivity.KEY_INSTANCES, mInstanceIds);
            i.putExtra(FormEntryActivity.KEY_INSTANCEID, mInstanceIds.get(0));
            i.putExtra(FormEntryActivity.KEY_FORMID, mFormId);            
            startActivity(i);

            setProgressVisibility(false);
        }
    }
    
    /*
     * Refresh the main form browser view as requested by the user
     */
    private class RefreshViewTask extends AsyncTask<InstanceDocument.Status, Integer, InstanceDocument.Status>
    {
        private ArrayList<FormDocument> documents = new ArrayList<FormDocument>();
        private Map<String, String> instanceTallies = new HashMap<String, String>();

        @Override
        protected InstanceDocument.Status doInBackground(InstanceDocument.Status... status)
        {
            if (status[0] == InstanceDocument.Status.nothing) {
                try {
                    documents = (ArrayList<FormDocument>) new FormRepository(Collect.mDb.getDb()).getAll();
                    DocumentUtils.sortByName(documents);
                } catch (ClassCastException e) {
                    // TODO: is there a better way to handle empty lists?
                }
            } else {
                instanceTallies = new FormRepository(Collect.mDb.getDb()).getFormsByInstanceStatus(status[0]);
                
                if (!instanceTallies.isEmpty()) {
                    documents = (ArrayList<FormDocument>) new FormRepository(Collect.mDb.getDb()).getAllByKeys(new ArrayList<Object>(instanceTallies.keySet()));                    
                    DocumentUtils.sortByName(documents);
                }
            }

            return status[0];
        }

        @Override
        protected void onPreExecute()
        {
            setProgressVisibility(true);
        }

        @Override
        protected void onPostExecute(InstanceDocument.Status status)
        {
            /*
             * Special hack to ensure that our application doesn't crash if we terminate it
             * before the AsyncTask has finished running.  This is stupid and I don't know
             * another way around it.
             * 
             * See http://dimitar.me/android-displaying-dialogs-from-background-threads/
             */
            if (isFinishing())
                return;
            
            BrowserListAdapter adapter = new BrowserListAdapter(
                    getApplicationContext(),
                    R.layout.main_browser_list_item, 
                    documents,
                    instanceTallies, 
                    (Spinner) findViewById(R.id.form_filter));
            
            setListAdapter(adapter);

            if (status == InstanceDocument.Status.nothing) {
                // Provide hints to user
                if (documents.isEmpty()) {
                    TextView nothingToDisplay = (TextView) findViewById(R.id.nothingToDisplay);
                    nothingToDisplay.setVisibility(View.VISIBLE);
                    
//                    Toast.makeText(getApplicationContext(), getString(R.string.tf_add_form_hint), Toast.LENGTH_LONG).show();
//                    openOptionsMenu();
                } else {
                    Toast.makeText(getApplicationContext(), getString(R.string.tf_begin_instance_hint), Toast.LENGTH_SHORT).show();
                }
            } else {
                Spinner s1 = (Spinner) findViewById(R.id.form_filter);
                String descriptor = s1.getSelectedItem().toString().toLowerCase();

                // Provide hints to user
                if (documents.isEmpty()) {
                    TextView nothingToDisplay = (TextView) findViewById(R.id.nothingToDisplay);
                    nothingToDisplay.setVisibility(View.VISIBLE);
                } else {
                    Toast.makeText(getApplicationContext(), getString(R.string.tf_browse_instances_hint, descriptor), Toast.LENGTH_SHORT).show();
                }
            }

            setProgressVisibility(false);
        }
    }
    
    /*
     * An initial connection error should be handled differently depending on
     * a) whether this device has already been registered, and
     * b) whether this device has a local (and properly initialized) CouchDB installation
     */
    private void displayConnectionErrorDialog(boolean registered)
    {
        mAlertDialog = new AlertDialog.Builder(this).create();
        
        mAlertDialog.setCancelable(false);
        mAlertDialog.setIcon(R.drawable.ic_dialog_alert);        
        mAlertDialog.setTitle(R.string.tf_connection_error);
        
        if (registered) 
            mAlertDialog.setMessage("");
        else 
            mAlertDialog.setMessage(getString(R.string.tf_connection_error_msg));
                
        mAlertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getText(R.string.tf_retry), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                new InitializeApplicationTask().execute(getApplicationContext());
            }
        });
        
//        mAlertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, getText(R.string.tf_go_offline), new DialogInterface.OnClickListener() {
//            public void onClick(DialogInterface dialog, int whichButton) {
//                // Continue and work offline -- only valid for registered users with a local CouchDB installation
//            }
//        });     
                        
        mAlertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getText(R.string.tf_exit_inform), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                finish();
            }
        });
                
        mAlertDialog.show();
    }

    private void displayErrorDialog(String errorMsg, final boolean shouldExit)
    {
        mAlertDialog = new AlertDialog.Builder(this).create();
        mAlertDialog.setCancelable(false);
        mAlertDialog.setIcon(R.drawable.ic_dialog_alert);
        mAlertDialog.setMessage(errorMsg);

        DialogInterface.OnClickListener errorListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i)
            {
                switch (i) {
                case DialogInterface.BUTTON1:
                    if (shouldExit) {
                        finish();
                    }
                    
                    break;
                }
            }
        };
        
        mAlertDialog.setButton(getString(R.string.ok), errorListener);
        mAlertDialog.show();
    }

    /**
     * Load the various elements of the screen that must wait for other tasks to
     * complete
     */
    private void loadScreen()
    {
        // Spinner must reflect results of refresh view below
        Spinner s1 = (Spinner) findViewById(R.id.form_filter);        
        triggerRefresh(s1.getSelectedItemPosition());
              
        registerForContextMenu(getListView());
    }
    
    /*
     * After InitializeApplicationTask() has completed this (may) be run to 
     * determine where to send the user according to whether the device was 
     * determined to be registered.
     */
    private void postInitializeWorkflow(boolean registered)
    {
        if (registered) {
            // Initialization is complete
            Collect.getInstance().getInformOnline().setReady(true);
            
            Intent i = new Intent(getApplicationContext(), MainBrowserActivity.class);
            startActivity(i);
            finish();
        } else {
            Intent i = new Intent(getApplicationContext(), ClientRegistrationActivity.class);
            startActivity(i);
            finish();
        }
    }
    
    private void setProgressVisibility(boolean visible)
    {
        ProgressBar pb = (ProgressBar) getWindow().findViewById(R.id.titleProgressBar);
        
        if (pb != null) {
            if (visible) {
                pb.setVisibility(View.VISIBLE);
            } else {
                pb.setVisibility(View.GONE);
            }
        }
    }
    
    private void triggerRefresh(int position)
    {
        // Hide "nothing to display" message
        TextView nothingToDisplay = (TextView) findViewById(R.id.nothingToDisplay);
        nothingToDisplay.setVisibility(View.INVISIBLE);
        
        mRefreshViewTask = new RefreshViewTask();

        switch (position) {
        // Show all forms (in group)
        case 0:
            mRefreshViewTask.execute(InstanceDocument.Status.nothing);
            break;
        // Show all draft forms
        case 1:
            mRefreshViewTask.execute(InstanceDocument.Status.draft);
            break;
        // Show all completed forms
        case 2:
            mRefreshViewTask.execute(InstanceDocument.Status.complete);
            break;
        // Show all unread forms (e.g., those added or updated by others)
        case 3:
            mRefreshViewTask.execute(InstanceDocument.Status.updated);
            break;
        }   
    }

    /**
     * displaySplash
     * 
     * Shows the splash screen if the mShowSplash member variable is true.
     * Otherwise a no-op.
     */
    void displaySplash()
    {
        if (!mShowSplash)
            return;
    
        // Fetch the splash screen Drawable
        Drawable image = null;
    
        try {
            // Attempt to load the configured default splash screen
            // The following code only works in 1.6+
            // BitmapDrawable bitImage = new BitmapDrawable(getResources(), FileUtils.SPLASH_SCREEN_FILE_PATH);
            BitmapDrawable bitImage = new BitmapDrawable(FileUtils.SPLASH_SCREEN_FILE_PATH);
    
            if (bitImage.getBitmap() != null
                    && bitImage.getIntrinsicHeight() > 0
                    && bitImage.getIntrinsicWidth() > 0) {
                image = bitImage;
            }
        } catch (Exception e) {
            // TODO: log exception for debugging?
        }
    
        if (image == null) {
            // no splash provided...
            if (FileUtils.storageReady() && !((new File(FileUtils.DEFAULT_CONFIG_PATH)).exists())) {
                // Show the built-in splash image if the config directory 
                // does not exist. Otherwise, suppress the icon.
                image = getResources().getDrawable(R.drawable.gc_color);
            }
            
            if (image == null) 
                return;
        }
    
        // Create ImageView to hold the Drawable...
        ImageView view = new ImageView(getApplicationContext());
    
        // Initialise it with Drawable and full-screen layout parameters
        view.setImageDrawable(image);
        
        int width = getWindowManager().getDefaultDisplay().getWidth();
        int height = getWindowManager().getDefaultDisplay().getHeight();
        
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height, 0);
        
        view.setLayoutParams(lp);
        view.setScaleType(ScaleType.CENTER);
        view.setBackgroundColor(Color.WHITE);
    
        // And wrap the image view in a frame layout so that the full-screen layout parameters are honoured
        FrameLayout layout = new FrameLayout(getApplicationContext());
        layout.addView(view);
    
        // Create the toast and set the view to be that of the FrameLayout
        Toast t = Toast.makeText(getApplicationContext(), "splash screen", Toast.LENGTH_LONG);
        t.setView(layout);
        t.setGravity(Gravity.CENTER, 0, 0);
        t.show();
    }
}
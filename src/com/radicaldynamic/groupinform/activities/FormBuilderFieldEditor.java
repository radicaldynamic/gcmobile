package com.radicaldynamic.groupinform.activities;

import java.util.ArrayList;
import java.util.Iterator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.method.DigitsKeyListener;
import android.text.method.QwertyKeyListener;
import android.text.method.TextKeyListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.radicaldynamic.groupinform.R;
import com.radicaldynamic.groupinform.application.Collect;
import com.radicaldynamic.groupinform.xform.Field;

public class FormBuilderFieldEditor extends Activity
{
    private static final String t = "FormBuilderElementEditor: ";
    
    public static final String KEY_FIELDTYPE = "fieldtype";
    public static final String KEY_SELECTDEFAULT = "selectinstancedefault";
    
    private static final int REQUEST_ITEMLIST = 1;
    
    private static final int MENU_ADVANCED = Menu.FIRST;
    private static final int MENU_ITEMS = Menu.FIRST + 1;
    private static final int MENU_HELP = Menu.FIRST + 2;
    
    private AlertDialog mAlertDialog;
    
    private Field mField = null;
    private String mFieldType = null;
    
    // Header
    private TextView mHeaderType;
    private ImageView mHeaderIcon;
    
    // Common input elements
    private EditText mLabel;
    private EditText mHint;
    private EditText mDefaultValue;
    private CheckBox mReadonly;
    private CheckBox mRequired;
    
    // Special hack to deal with the added complexity of selecte fields
    private String mSelectInstanceDefault = "";
    
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.fb_field_editor);  
        
        // Retrieve field (if any)
        mField = Collect.getInstance().getFbField();

        // Create a new field if one is needed (further init will occur in the field-specific method)
        if (mField == null)
            mField = new Field();     
        
        // If there is no instance state (e.g., this activity was loaded by another/this is not a flip)
        if (savedInstanceState == null) {           
            Intent i = getIntent();
            mFieldType = i.getStringExtra(KEY_FIELDTYPE);
            
            // We store off the instance default string for select types (this is special)
            if (mFieldType.equals("select")) {
                mSelectInstanceDefault = mField.getInstance().getDefaultValue();
                
                // This is becoming problematic but we must reset this somewhere
                Collect.getInstance().setFbItemList(null);
            }
        } else {
            if (savedInstanceState.containsKey(KEY_FIELDTYPE))
                mFieldType = savedInstanceState.getString(KEY_FIELDTYPE);
            
            if (savedInstanceState.containsKey(KEY_SELECTDEFAULT))
                mSelectInstanceDefault = savedInstanceState.getString(KEY_SELECTDEFAULT);
        }

        String title = "";
            
        // Activity should have a relevant title (e.g., add new or edit existing)
        if (mField.isNewField()) {
            title = getString(R.string.tf_add_new) + " "
                    + mFieldType.substring(0, 1).toUpperCase() + mFieldType.substring(1) + " " + getString(R.string.tf_field);
        } else {
            title = getString(R.string.tf_edit) + " "
                    + mFieldType.substring(0, 1).toUpperCase() + mFieldType.substring(1) + " " + getString(R.string.tf_field);            
        }
        
        setTitle(getString(R.string.app_name) + " > " + title);
        
        // Get a handle on common input elements
        mLabel          = (EditText) findViewById(R.id.label);
        mHint           = (EditText) findViewById(R.id.hint);
        mDefaultValue   = (EditText) findViewById(R.id.defaultValue);
        mReadonly       = (CheckBox) findViewById(R.id.readonly);
        mRequired       = (CheckBox) findViewById(R.id.required);
        
        // New strings in either the label or hint should begin with a capital by default
        mLabel.setKeyListener(new QwertyKeyListener(TextKeyListener.Capitalize.SENTENCES, false));
        mHint.setKeyListener(new QwertyKeyListener(TextKeyListener.Capitalize.SENTENCES, false));
        
        // Set up listener to detect changes to read-only input element
        mReadonly.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {                
                if (((CheckBox) v).isChecked())
                    mRequired.setChecked(false);                 
            }
        });
        
        // Set up listener to detect changes to required input element
        mRequired.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {                
                if (((CheckBox) v).isChecked())
                    mReadonly.setChecked(false);                 
            }
        });

        if (mFieldType.equals("barcode"))         loadBarcodeElement();                    
        else if (mFieldType.equals("date"))       loadDateElement();                
        else if (mFieldType.equals("geopoint"))   loadGeopointElement();                  
        else if (mFieldType.equals("group"))      loadGroupElement();                    
        else if (mFieldType.equals("media"))      loadMediaElement();                    
        else if (mFieldType.equals("number"))     loadNumberElement();                    
        else if (mFieldType.equals("select"))     loadSelectElement();                    
        else if (mFieldType.equals("text"))       loadTextElement();                    
        else 
            Log.w(Collect.LOGTAG, t + "unhandled field type");
        
        // Set up header
        mHeaderType     = (TextView) findViewById(R.id.headerType);
        mHeaderIcon     = (ImageView) findViewById(R.id.headerIcon);
        
        // Field type string
        mHeaderType.setText(mFieldType.substring(0, 1).toUpperCase() + mFieldType.substring(1) + " " + getString(R.string.tf_field));
        
        /*
         * Set header icon
         * 
         * TODO: figure out a better way to do with without duplicating code from FormBuilderFieldListAdapter
         */        
        if (mField.getType().equals("group")) {
            mHeaderIcon.setImageDrawable(getDrawable(R.drawable.element_group));            
        } else if (mField.getType().equals("input")) {
            Drawable icon = getDrawable(R.drawable.element_string);
            
            try {
                String specificType = mField.getBind().getType();
                
                if (specificType.equals("barcode"))     icon = getDrawable(R.drawable.element_barcode);     else
                if (specificType.equals("date"))        icon = getDrawable(R.drawable.element_calendar);    else
                if (specificType.equals("decimal"))     icon = getDrawable(R.drawable.element_number);      else
                if (specificType.equals("geopoint"))    icon = getDrawable(R.drawable.element_location);    else
                if (specificType.equals("int"))         icon = getDrawable(R.drawable.element_number);
            } catch (NullPointerException e){
                // TODO: is this really a problem?    
            } finally {
                mHeaderIcon.setImageDrawable(icon);
            }            
        } else if (mField.getType().equals("repeat")) { 
            mHeaderIcon.setImageDrawable(getDrawable(R.drawable.element_group));          
        } else if (mField.getType().equals("select")) {
            mHeaderIcon.setImageDrawable(getDrawable(R.drawable.element_selectmulti));
        } else if (mField.getType().equals("select1")) {
            mHeaderIcon.setImageDrawable(getDrawable(R.drawable.element_selectsingle));
        } else if (mField.getType().equals("trigger")) {
            mHeaderIcon.setImageDrawable(getDrawable(R.drawable.element_noicon));            
        } else if (mField.getType().equals("upload")) {
            mHeaderIcon.setImageDrawable(getDrawable(R.drawable.element_media));            
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        switch (requestCode) {
        case REQUEST_ITEMLIST:
            if (resultCode == RESULT_OK) {
                // Preselected list items may have changed, store off this list
                ArrayList<String> defaults = new ArrayList<String>();
                
                Iterator<Field> it = Collect.getInstance().getFbItemList().iterator();
                
                while (it.hasNext()) {
                    Field item = it.next();

                    // If an item is marked as a default (preselected)
                    if (item.isItemDefault()) {
                        defaults.add(item.getItemValue());
                        item.setItemDefault(false);
                    }
                }
                
                /* 
                 * This will either be processed by saveSelectElement() or sent back to 
                 * FormBuilderSelectItemList by way of onOptionsItemSelected() 
                 */
                mSelectInstanceDefault = defaults.toString().replaceAll(",\\s", " ").replaceAll("[\\[\\]]", "");
            }
            
            break;
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        super.onCreateOptionsMenu(menu);
        
        menu.add(0, MENU_ADVANCED, 0, getString(R.string.tf_advanced))
            .setIcon(R.drawable.options);
        
        menu.add(0, MENU_ITEMS, 0, getString(R.string.tf_list_items))
            .setIcon(R.drawable.ic_menu_mark)
            .setEnabled(mFieldType.equals("select") ? true : false);        
        
        menu.add(0, MENU_HELP, 0, getString(R.string.tf_help))
            .setIcon(R.drawable.ic_menu_help);
        
        return true;
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        switch (keyCode) {
        case KeyEvent.KEYCODE_BACK:
            createQuitDialog();
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }    
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId()) {
        // Launch the advanced properties editor
        case MENU_ADVANCED:
            break;
         
        // Launch the form builder select item editor
        case MENU_ITEMS:
            Intent i = new Intent(this, FormBuilderSelectItemList.class);            
            /* 
             * Use the state of the select radio option to determine whether to indicate to
             * the select item list which mode the select list is operating in. 
             * 
             * This makes sense because the user may have switched select modes but may
             * not have saved the field yet, so we cannot determine this from the field itself.
             */
            final RadioButton radioSingle = (RadioButton) findViewById(R.id.selectTypeSingle);            
            i.putExtra(FormBuilderSelectItemList.KEY_SINGLE, radioSingle.isChecked());
            i.putExtra(FormBuilderSelectItemList.KEY_DEFAULT, mSelectInstanceDefault);
            startActivityForResult(i, REQUEST_ITEMLIST);
            break;
            
         // TODO: display field-specific help text (from web site)
        case MENU_HELP:
            break;
        }
    
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_FIELDTYPE, mFieldType);
        outState.putString(KEY_SELECTDEFAULT, mSelectInstanceDefault);
        
        // Save this specific field state for orientation changes & select item editor
        Collect.getInstance().setFbField(mField);
    }

    private void createQuitDialog()
    {
        String[] items = {
                getString(R.string.do_not_save),
                getString(R.string.tf_save_field_and_exit), 
                getString(R.string.tf_do_not_exit_field)
        };
    
        mAlertDialog = new AlertDialog.Builder(this)
            .setIcon(R.drawable.ic_dialog_alert)
            .setTitle(getString(R.string.quit_application))
            .setItems(items,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        switch (which) {
                        case 0:
                            // Discard any changes and exit
                            setResult(RESULT_CANCELED);
                            finish();
                            break;
    
                        case 1:
                            // Save and exit
                            if (saveChanges()) {
                                setResult(RESULT_OK);
                                finish();
                            }
                            break;
    
                        case 2:
                            // Do nothing
                            break;    
                        }
                    }
                }).create();
    
        mAlertDialog.show();
    }
    
    // See loadSelectElement() for further information on this dialog
    private void createSelectChangeDialog()
    {
        mAlertDialog = new AlertDialog.Builder(this)
            .setCancelable(false)
            .setIcon(R.drawable.ic_dialog_alert)
            .setTitle(R.string.tf_change_select_type)
            .setMessage(R.string.tf_change_select_type_message)            
            .setPositiveButton(R.string.tf_yes, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    mSelectInstanceDefault = "";
                }
            })
            .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    // Return to a multiple select field type
                    RadioButton radioMultiple = (RadioButton) findViewById(R.id.selectTypeMultiple);
                    radioMultiple.setChecked(true);
                    
                    dialog.cancel();
                }
            }).create();
        
        mAlertDialog.show();
    }
    
    /*
     * The field editor layout file includes all possible input elements for all field types.
     * Since this will not make sense to the end user our approach is to simply hide unneeded
     * fields based on the human field type that was passed to this activity when it was started.
     * 
     * This method exists to make the hiding of input elements easier.
     */
    private void disableFormComponent(int componentResource)
    {
        ViewGroup component = (ViewGroup) findViewById(componentResource);
        component.setVisibility(View.GONE);
    }
    
    // Convenience method
    private Drawable getDrawable(int image)
    {
        return getResources().getDrawable(image);
    }
    
    /*
     * Initialize input elements that are likely to appear for all field types.
     * This should be done AFTER any primary initialization of newly created fields.
     */
    private void loadCommonAttributes()
    {         
         mLabel.setText(mField.getLabel().toString());
         mHint.setText(mField.getHint().toString());
         mDefaultValue.setText(mField.getInstance().getDefaultValue());
         
         if (mField.getBind().isReadonly())
             mReadonly.setChecked(true);
         
         if (mField.getBind().isRequired())
             mRequired.setChecked(true);
    }
    
    private void loadBarcodeElement()
    {
        // Further initialize newly created fields
        if (mField.isEmpty()) {
            mField.setType("input");                    
            mField.getBind().setType(mFieldType);
            mField.setEmpty(false);
        }
        
        loadCommonAttributes();
        
        disableFormComponent(R.id.groupFieldTypeSelection);
        disableFormComponent(R.id.mediaFieldTypeSelection);
        disableFormComponent(R.id.numberFieldTypeSelection);
        disableFormComponent(R.id.selectFieldTypeSelection);
        disableFormComponent(R.id.readonlyLayout);
    }
    
    // TODO: add support for selecting times as well as dates once this becomes available
    private void loadDateElement()
    {
        // Further initialize newly created fields
        if (mField.isEmpty()) {
            mField.setType("input");                    
            mField.getBind().setType(mFieldType);
            mField.setEmpty(false);
        }
        
        loadCommonAttributes();
     
        disableFormComponent(R.id.groupFieldTypeSelection);
        disableFormComponent(R.id.mediaFieldTypeSelection);
        disableFormComponent(R.id.numberFieldTypeSelection);
        disableFormComponent(R.id.selectFieldTypeSelection);
        
        // TODO: we should probably display a "default" date using the date widget
    }
    
    private void loadGeopointElement()
    {
        // Further initialize newly created fields
        if (mField.isEmpty()) {
            mField.setType("input");
            mField.getBind().setType(mFieldType);
            mField.setEmpty(false);
        }
        
        loadCommonAttributes();
        
        disableFormComponent(R.id.defaultValueInput);
        disableFormComponent(R.id.groupFieldTypeSelection);        
        disableFormComponent(R.id.mediaFieldTypeSelection);
        disableFormComponent(R.id.numberFieldTypeSelection);
        disableFormComponent(R.id.selectFieldTypeSelection);
        disableFormComponent(R.id.readonlyLayout);
    }
    
    private void loadGroupElement()
    {
        // Further initialize newly created fields
        if (mField.isEmpty()) {
            mField.setType("group");
            mField.setEmpty(false);
        }
        
        loadCommonAttributes();
    
        disableFormComponent(R.id.defaultValueInput);
        disableFormComponent(R.id.hintInput);
        disableFormComponent(R.id.mediaFieldTypeSelection);
        disableFormComponent(R.id.numberFieldTypeSelection);
        disableFormComponent(R.id.selectFieldTypeSelection);
        disableFormComponent(R.id.readonlyLayout);
        disableFormComponent(R.id.requiredLayout);
        
        final RadioButton groupRegular = (RadioButton) findViewById(R.id.groupTypeRegular);
        final RadioButton groupRepeated = (RadioButton) findViewById(R.id.groupTypeRepeated);
        
        // Initialize group type selection
        if (Field.isRepeatedGroup(mField))
            groupRepeated.setChecked(true);
        else
            groupRegular.setChecked(true);
    }
    
    private void loadMediaElement()
    {
        // Further initialize newly created fields
        if (mField.isEmpty()) {
            mField.setType("upload");
            mField.getBind().setType("binary");
            mField.getAttributes().put("mediatype", "image/*");
            mField.setEmpty(false);
        }
        
        loadCommonAttributes();

        disableFormComponent(R.id.defaultValueInput);
        disableFormComponent(R.id.groupFieldTypeSelection);        
        disableFormComponent(R.id.numberFieldTypeSelection);
        disableFormComponent(R.id.selectFieldTypeSelection);
        disableFormComponent(R.id.readonlyLayout);        
        
        // Set up listener for radio buttons so that they influence the field type
        OnClickListener radioListener = new OnClickListener() {
            public void onClick(View v) {
                RadioButton rb = (RadioButton) v;
                
                switch (rb.getId()) {
                case R.id.mediaTypeAudio: break;                    
                case R.id.mediaTypeImage: break;
                case R.id.mediaTypeVideo: break;
                }
            }
        };
        
        final RadioButton radioAudio = (RadioButton) findViewById(R.id.mediaTypeAudio);
        final RadioButton radioImage = (RadioButton) findViewById(R.id.mediaTypeImage);
        final RadioButton radioVideo = (RadioButton) findViewById(R.id.mediaTypeVideo);
        
        radioAudio.setOnClickListener(radioListener);
        radioImage.setOnClickListener(radioListener);
        radioVideo.setOnClickListener(radioListener);
        
        // Initialize media type selection
        if (mField.getAttributes().get("mediatype").equals("audio/*"))
            radioAudio.setChecked(true);
        else if (mField.getAttributes().get("mediatype").equals("image/*"))
            radioImage.setChecked(true);
        else if (mField.getAttributes().get("mediatype").equals("video/*"))
            radioVideo.setChecked(true);
    }
    
    private void loadNumberElement()
    {
        // Further initialize newly created fields
        if (mField.isEmpty()) {
            mField.setType("input");
            mField.getBind().setType("int");            
            mField.setEmpty(false);
        }
        
        loadCommonAttributes();
        
        disableFormComponent(R.id.groupFieldTypeSelection);
        disableFormComponent(R.id.mediaFieldTypeSelection);
        disableFormComponent(R.id.selectFieldTypeSelection);
        
        final EditText defaultValue = (EditText) findViewById(R.id.defaultValue);
        
        // Set up listener for radio buttons so that they influence the field type
        OnClickListener radioListener = new OnClickListener() {
            public void onClick(View v) {
                RadioButton rb = (RadioButton) v;
                
                switch (rb.getId()) {
                case R.id.numberTypeInteger:
                    defaultValue.setKeyListener(new DigitsKeyListener(false, false));
                    
                    // Remove any occurrences of a decimal
                    if (defaultValue.getText().toString().contains(".")) {
                        String txt = defaultValue.getText().toString();                        
                        defaultValue.setText(txt.replace(".", ""));
                    }
                    
                    break;
                    
                case R.id.numberTypeDecimal:                                   
                    defaultValue.setKeyListener(new DigitsKeyListener(false, true)); 
                    break;                    
                }
            }
        };
        
        final RadioButton radioInteger = (RadioButton) findViewById(R.id.numberTypeInteger);
        final RadioButton radioDecimal = (RadioButton) findViewById(R.id.numberTypeDecimal);
        
        radioInteger.setOnClickListener(radioListener);
        radioDecimal.setOnClickListener(radioListener);
        
        // Initialize number type selection
        if (mField.getBind().getType().equals("int")) { 
            radioInteger.setChecked(true);
            // false, false supports only integer input
            defaultValue.setKeyListener(new DigitsKeyListener(false, false));
        } else {
            radioDecimal.setChecked(true);
            // false, true supports decimal input
            defaultValue.setKeyListener(new DigitsKeyListener(false, true));
        }
    }
    
    private void loadSelectElement()
    {
        // Further initialize newly created fields
        if (mField.isEmpty()) {
            mField.setType(mFieldType);
            mField.getBind().setType(mFieldType);     
            mField.setEmpty(false);
        }
        
        loadCommonAttributes();
        
        disableFormComponent(R.id.defaultValueInput);
        disableFormComponent(R.id.groupFieldTypeSelection);        
        disableFormComponent(R.id.mediaFieldTypeSelection);
        disableFormComponent(R.id.numberFieldTypeSelection);
        disableFormComponent(R.id.readonlyLayout);
        
        // Set up listener for radio buttons so that they influence the field type
        OnClickListener radioListener = new OnClickListener() {
            public void onClick(View v) {
                RadioButton rb = (RadioButton) v;
                
                switch (rb.getId()) {
                case R.id.selectTypeMultiple:
                    mHeaderIcon.setImageDrawable(getDrawable(R.drawable.element_selectmulti));                    
                    break;                    
                case R.id.selectTypeSingle:
                    mHeaderIcon.setImageDrawable(getDrawable(R.drawable.element_selectsingle));
                    
                    /* 
                     * Single selects may only have one preselected default.  This presents a problem
                     * if the user is switching from a multiple to a single default and requires user
                     * intervention.
                     */
                    if (mSelectInstanceDefault.split("\\s+").length > 1)
                        createSelectChangeDialog();
                                        
                    break;
                }
            }
        };
        
        final RadioButton radioMultiple = (RadioButton) findViewById(R.id.selectTypeMultiple);
        final RadioButton radioSingle = (RadioButton) findViewById(R.id.selectTypeSingle);
        
        radioMultiple.setOnClickListener(radioListener);
        radioSingle.setOnClickListener(radioListener);

        // Initialize select type 
        if (mField.getType().equals("select"))
            radioMultiple.setChecked(true);
        else
            radioSingle.setChecked(true);        
    }
    
    private void loadTextElement()
    {
        // Further initialize newly created fields
        if (mField.isEmpty()) {
            mField.setType("input");
            mField.getBind().setType("string"); 
            mField.setEmpty(false);
        }
        
        loadCommonAttributes();
        
        disableFormComponent(R.id.groupFieldTypeSelection);
        disableFormComponent(R.id.mediaFieldTypeSelection);
        disableFormComponent(R.id.numberFieldTypeSelection);
        disableFormComponent(R.id.selectFieldTypeSelection);
    } 
    
    /*
     * Save any changes that the user has made to the form field
     */
    private boolean saveChanges()
    {
        // Ensure that a label is present
        if (mLabel.getText().toString().replaceAll("\\s+", "").length() == 0) {
            mLabel.requestFocusFromTouch();
            
            Toast.makeText(
                    getApplicationContext(), 
                    getString(R.string.tf_field_cannot_be_saved), 
                    Toast.LENGTH_SHORT).show();            
            
            return false;            
        }
        
        // Save common attributes
        mField.setLabel(mLabel.getText().toString().trim());
        mField.setHint(mHint.getText().toString().trim());
        mField.getInstance().setDefaultValue(mDefaultValue.getText().toString().trim());        
        
        if (mReadonly.isChecked())
            mField.getBind().setReadonly(true);
        else 
            mField.getBind().setReadonly(false);
            
        if (mRequired.isChecked())
            mField.getBind().setRequired(true);
        else 
            mField.getBind().setRequired(false);
        
        // Save (control) field-specific properties 
        if (mFieldType.equals("barcode"))         saveBarcodeElement();                    
        else if (mFieldType.equals("date"))       saveDateElement();                
        else if (mFieldType.equals("geopoint"))   saveGeopointElement();                  
        else if (mFieldType.equals("group"))      saveGroupElement();                    
        else if (mFieldType.equals("media"))      saveMediaElement();                    
        else if (mFieldType.equals("number"))     saveNumberElement();                    
        else if (mFieldType.equals("select"))     saveSelectElement();                    
        else if (mFieldType.equals("text"))       saveTextElement();                    
        else 
            Log.w(Collect.LOGTAG, t + "unhandled field type");
        
        // Mark the field as having been saved
        mField.setSaved(true);
        
        Collect.getInstance().setFbField(mField);
        
        return true;
    }
    
    private void saveBarcodeElement() 
    {

    }
    
    private void saveDateElement()
    {
        
    }
    
    private void saveGeopointElement()
    {
        
    }

    /*
     * FIXME: update children references after changing between group types
     */
    private void saveGroupElement()
    {
        final RadioButton groupRegular = (RadioButton) findViewById(R.id.groupTypeRegular);
        
        if (groupRegular.isChecked()) {
            // Changing from a repeated group to a regular group involves work
            if (Field.isRepeatedGroup(mField)) {
                // Move any children of the repeated group to the regular group
                mField.getChildren().addAll(mField.getRepeat().getChildren());
                
                // Remove the repeat tag itself
                if (mField.getChildren().size() > 0)
                    mField.getChildren().remove(0);
            }
        } else {
            // Changing from a regular group to a repeated group involves work
            if (!Field.isRepeatedGroup(mField)) {
                ArrayList<Field> regularGroupChildren = new ArrayList<Field>();
                
                // Store off the children and remove them from the group
                if (!mField.getChildren().isEmpty()) {                                        
                    regularGroupChildren = mField.getChildren();
                    mField.getChildren().clear();
                }
                    
                // Create the repeat field and add it to the group
                Field repeat = new Field();
                repeat.setType("repeat");

                mField.getChildren().add(repeat);

                if (!regularGroupChildren.isEmpty())
                    mField.getRepeat().getChildren().addAll(regularGroupChildren);
            }
        }
            
    }
    
    private void saveMediaElement()
    {
        final RadioButton radioAudio = (RadioButton) findViewById(R.id.mediaTypeAudio);
        final RadioButton radioImage = (RadioButton) findViewById(R.id.mediaTypeImage);
        final RadioButton radioVideo = (RadioButton) findViewById(R.id.mediaTypeVideo);
        
        if (radioAudio.isChecked()) {
            mField.getAttributes().put("mediatype", "audio/*");
        } else if (radioImage.isChecked()) {
            mField.getAttributes().put("mediatype", "image/*"); 
        } else if (radioVideo.isChecked()) {
            mField.getAttributes().put("mediatype", "video/*"); 
        }        
    }
    
    private void saveNumberElement()
    {
        final RadioButton radioInteger = (RadioButton) findViewById(R.id.numberTypeInteger);
        
        if (radioInteger.isChecked()) {
            mField.getBind().setType("int");
        } else {
            mField.getBind().setType("decimal");
        }
    }
    
    @SuppressWarnings("unchecked")
    private void saveSelectElement()
    {
        final RadioButton radioSingle = (RadioButton) findViewById(R.id.selectTypeSingle);
        
        if (radioSingle.isChecked()) {
            mField.setType("select1");
            mField.getBind().setType("select1");
        } else {
            mField.setType("select");
            mField.getBind().setType("select");                        
        }   
        
        // Save any changes to the list of items
        if (Collect.getInstance().getFbItemList() != null) {            
            mField.getChildren().clear();
            mField.getChildren().addAll((ArrayList<Field>) Collect.getInstance().getFbItemList().clone());
            
            Collect.getInstance().setFbItemList(null);
        }
        
        mField.getInstance().setDefaultValue(mSelectInstanceDefault);
    }
    
    private void saveTextElement()
    {
        
    }
}
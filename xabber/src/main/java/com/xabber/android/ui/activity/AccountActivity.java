package com.xabber.android.ui.activity;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.NavUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.MultiTransformation;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.zxing.integration.android.IntentIntegrator;
import com.xabber.android.R;
import com.xabber.android.data.Application;
import com.xabber.android.data.account.AccountErrorEvent;
import com.xabber.android.data.account.AccountItem;
import com.xabber.android.data.account.AccountManager;
import com.xabber.android.data.account.listeners.OnAccountChangedListener;
import com.xabber.android.data.entity.AccountJid;
import com.xabber.android.data.entity.UserJid;
import com.xabber.android.data.extension.blocking.BlockingManager;
import com.xabber.android.data.extension.blocking.OnBlockedListChangedListener;
import com.xabber.android.data.intent.AccountIntentBuilder;
import com.xabber.android.data.log.LogManager;
import com.xabber.android.data.roster.AbstractContact;
import com.xabber.android.data.roster.RosterContact;
import com.xabber.android.data.roster.RosterManager;
import com.xabber.android.data.xaccount.XabberAccountManager;
import com.xabber.android.ui.adapter.accountoptions.AccountOption;
import com.xabber.android.ui.adapter.accountoptions.AccountOptionsAdapter;
import com.xabber.android.ui.color.ColorManager;
import com.xabber.android.ui.dialog.AccountColorDialog;
import com.xabber.android.ui.fragment.ContactVcardViewerFragment;
import com.xabber.android.ui.helper.BlurTransformation;
import com.xabber.android.ui.helper.ContactTitleInflater;

import org.greenrobot.eventbus.Subscribe;

import java.util.Collection;

public class AccountActivity extends ManagedActivity implements AccountOptionsAdapter.Listener,
        OnAccountChangedListener, OnBlockedListChangedListener, ContactVcardViewerFragment.Listener, MenuItem.OnMenuItemClickListener, View.OnClickListener, Toolbar.OnMenuItemClickListener {

    private static final String LOG_TAG = AccountActivity.class.getSimpleName();
    private static final String ACTION_CONNECTION_SETTINGS = AccountActivity.class.getName() + "ACTION_CONNECTION_SETTINGS";

    private AccountJid account;
    private UserJid fakeAccountUser;
    private AbstractContact bestContact;
    private AccountItem accountItem;

    private View contactTitleView;
    private View statusIcon;
    private View statusGroupIcon;
    private ImageView background;
    private ImageView qrCodeLand;
    private ImageView colorPickerLand;
    private MenuItem qrCodePortrait;
    private MenuItem colorPickerPortrait;
    private SwitchCompat switchCompat;
    private CollapsingToolbarLayout collapsingToolbar;
    private AppBarLayout appBarLayout;
    private Toolbar toolbar;
    private RecyclerView recyclerView;

    private AccountOptionsAdapter accountOptionsAdapter;
    private IntentIntegrator integrator;
    private boolean isConnectionSettingsAction;
    private int accountMainColor;
    private int orientation;

    public AccountActivity() {
    }

    private static AccountJid getAccount(Intent intent) {
        return AccountIntentBuilder.getAccount(intent);
    }

    @NonNull
    public static Intent createIntent(Context context, AccountJid account) {
        return new AccountIntentBuilder(context, AccountActivity.class).setAccount(account).build();
    }

    @NonNull
    public static Intent createConnectionSettingsIntent(Context context, AccountJid account) {
        Intent intent = new AccountIntentBuilder(context, AccountActivity.class).setAccount(account).build();
        intent.setAction(ACTION_CONNECTION_SETTINGS);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();

        integrator = new IntentIntegrator(this);

        account = getAccount(intent);
        if (account == null) {
            LogManager.i(LOG_TAG, "Account is null, finishing!");
            finish();
            return;
        }

        accountItem = AccountManager.getInstance().getAccount(account);
        if (accountItem == null) {
            Application.getInstance().onError(R.string.NO_SUCH_ACCOUNT);
            finish();
            return;
        }

        if (ACTION_CONNECTION_SETTINGS.equals(intent.getAction())) {
            isConnectionSettingsAction = true;
            startAccountSettingsActivity();
            setIntent(null);
        }

        setScreenWindowSettings();

        setContentView(R.layout.activity_account);

        toolbar = (Toolbar) findViewById(R.id.toolbar_default);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_left_white_24dp);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NavUtils.navigateUpFromSameTask(AccountActivity.this);
            }
        });

        toolbar.inflateMenu(R.menu.toolbar_account);

        MenuItem item = toolbar.getMenu().findItem(R.id.action_account_switch);
        switchCompat = (SwitchCompat) item.getActionView().findViewById(R.id.account_switch_view);
        switchCompat.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                AccountManager.getInstance().setEnabled(accountItem.getAccount(), isChecked);
            }
        });

        try {
            fakeAccountUser = UserJid.from(account.getFullJid().asBareJid());
        } catch (UserJid.UserJidCreateException e) {
            throw new IllegalStateException();
        }

        bestContact = RosterManager.getInstance().getBestContact(account, fakeAccountUser);

        accountMainColor = ColorManager.getInstance().getAccountPainter().getAccountMainColor(account);

        contactTitleView = findViewById(R.id.contact_title_expanded_new);
        TextView contactAddressView = (TextView) findViewById(R.id.address_text);
        contactAddressView.setText(account.getFullJid().asBareJid().toString());

        statusIcon = findViewById(R.id.ivStatus);
        statusGroupIcon = findViewById(R.id.ivStatusGroupchat);
        //statusText = (TextView) findViewById(R.id.status_text);

        //
        toolbar.setOnMenuItemClickListener(this);
        qrCodePortrait = toolbar.getMenu().findItem(R.id.action_generate_qrcode);
        colorPickerPortrait = toolbar.getMenu().findItem(R.id.action_account_color);
        //qrCodePortrait.setOnMenuItemClickListener(this);
        //
        recyclerView = (RecyclerView) findViewById(R.id.account_options_recycler_view);
        accountOptionsAdapter = new AccountOptionsAdapter(AccountOption.getValues(), this, accountItem);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(accountOptionsAdapter);
        recyclerView.setNestedScrollingEnabled(false);

        orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            orientationPortrait();
        } else {
            orientationLandscape();
        }

    }

    private void orientationPortrait() {
        collapsingToolbar = (CollapsingToolbarLayout) findViewById(R.id.collapsing_toolbar);
        appBarLayout = (AppBarLayout) findViewById(R.id.appbar);
        appBarLayout.addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {
            boolean isShow = true;
            int scrollRange = -1;
            @Override
            public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
                if (scrollRange == -1) {
                    scrollRange = appBarLayout.getTotalScrollRange();
                }
                if (scrollRange + verticalOffset == 0) {
                    collapsingToolbar.setTitle(bestContact.getName());
                    contactTitleView.setVisibility(View.INVISIBLE);
                    isShow = true;
                } else if (isShow) {
                    collapsingToolbar.setTitle(" ");
                    contactTitleView.setVisibility(View.VISIBLE);
                    isShow = false;
                }
            }
        });
        collapsingToolbar.setContentScrimColor(accountMainColor);
    }

    private void orientationLandscape() {
        final LinearLayout nameHolderView = (LinearLayout) findViewById(R.id.name_holder);
        toolbar.setBackgroundColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window win = getWindow();
            win.setStatusBarColor(accountMainColor);
        }

        qrCodePortrait.setVisible(false);
        qrCodeLand = findViewById(R.id.generate_qrcode);
        qrCodeLand.setOnClickListener(this);

        colorPickerPortrait.setVisible(false);
        colorPickerLand = findViewById(R.id.change_color);
        colorPickerLand.setOnClickListener(this);

        final LinearLayout ll = findViewById(R.id.scroll_view_child);
        nameHolderView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    nameHolderView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
                else nameHolderView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                int topPadding = (nameHolderView.getHeight());
                ll.setPadding(0,topPadding,0,0);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (AccountManager.getInstance().getAccount(account) == null) {
            // in case if account was removed
            finish();
            return;
        }
        updateTitle();
        updateOptions();
        Application.getInstance().addUIListener(OnAccountChangedListener.class, this);
        Application.getInstance().addUIListener(OnBlockedListChangedListener.class, this);
    }

    @Override
    protected void onPause() {
        Application.getInstance().removeUIListener(OnBlockedListChangedListener.class, this);
        Application.getInstance().removeUIListener(OnAccountChangedListener.class, this);

        isConnectionSettingsAction = false;
        super.onPause();
    }

    private void updateTitle() {
        ContactTitleInflater.updateTitle(contactTitleView, this, bestContact);
        statusIcon.setVisibility(View.GONE);
        statusGroupIcon.setVisibility(View.GONE);
        updateAccountColor();
        //statusText.setText(account.getFullJid().asBareJid().toString());
        switchCompat.setChecked(accountItem.isEnabled());
    }

    private void updateAccountColor() {
        accountMainColor = ColorManager.getInstance().getAccountPainter().getAccountMainColor(account);

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Window win = getWindow();
                win.setStatusBarColor(accountMainColor);
            }
        }

        if (collapsingToolbar != null)
            collapsingToolbar.setContentScrimColor(accountMainColor);

        background = findViewById(R.id.backgroundView);
        Drawable backgroundSource = bestContact.getAvatar(false);
        if (backgroundSource == null)
            backgroundSource = getResources().getDrawable(R.drawable.about_backdrop);
        Glide.with(this)
                .load(backgroundSource)
                .transform(new MultiTransformation<Bitmap>(new CenterCrop(), new BlurTransformation(25, 8, /*this,*/ accountMainColor)))
                .into(background);
    }

    private void updateOptions() {
        AccountOption.SYNCHRONIZATION.setDescription(getString(R.string.account_sync_summary));

        AccountOption.CONNECTION_SETTINGS.setDescription(account.getFullJid().asBareJid().toString());

        AccountOption.VCARD.setDescription(getString(R.string.account_vcard_summary));

        AccountOption.PUSH_NOTIFICATIONS.setDescription(getString(accountItem.isPushWasEnabled()
                ? R.string.account_push_state_enabled : R.string.account_push_state_disabled));

        //AccountOption.COLOR.setDescription(ColorManager.getInstance().getAccountPainter().getAccountColorName(account));

        updateBlockListOption();

        AccountOption.SERVER_INFO.setDescription(getString(R.string.account_server_info_description));

        AccountOption.CHAT_HISTORY.setDescription(getString(R.string.account_history_options_summary));

        AccountOption.BOOKMARKS.setDescription(getString(R.string.account_bookmarks_summary));

        accountOptionsAdapter.notifyDataSetChanged();
    }

    private void updateBlockListOption() {
        BlockingManager blockingManager = BlockingManager.getInstance();

        Boolean supported = blockingManager.isSupported(account);

        String description;
        if (supported == null) {
            description  = getString(R.string.blocked_contacts_unknown);
        } else if (!supported) {
            description  = getString(R.string.blocked_contacts_not_supported);
        } else {
            int size = blockingManager.getCachedBlockedContacts(account).size();
            if (size == 0) {
                description = getString(R.string.blocked_contacts_empty);
            } else {
                description = getResources().getQuantityString(R.plurals.blocked_contacts_number, size, size);
            }
        }

        AccountOption.BLOCK_LIST.setDescription(description);
        accountOptionsAdapter.notifyItemChanged(AccountOption.BLOCK_LIST.ordinal());
    }

    @Override
    public void onAccountOptionClick(AccountOption option) {
        switch (option) {
            case CONNECTION_SETTINGS:
                startAccountSettingsActivity();
                break;
            case VCARD:
                startActivity(AccountInfoEditActivity.createIntent(this, account));
                break;
            case PUSH_NOTIFICATIONS:
                startActivity(AccountPushActivity.createIntent(this, account));
                break;
            //case COLOR:
            //    AccountColorDialog.newInstance(account).show(getFragmentManager(),
            //            AccountColorDialog.class.getSimpleName());
            //   break;
            case BLOCK_LIST:
                startActivity(BlockedListActivity.createIntent(this, account));
                break;
            case SERVER_INFO:
                startActivity(ServerInfoActivity.createIntent(this, account));
                break;
            case CHAT_HISTORY:
                startActivity(AccountHistorySettingsActivity.createIntent(this, account));
                break;
            case BOOKMARKS:
                startActivity(BookmarksActivity.createIntent(this, account));
                break;
            case SYNCHRONIZATION:
                if (XabberAccountManager.getInstance().getAccount() != null) {
                    if (accountItem.isSyncNotAllowed()) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        builder.setMessage(R.string.sync_not_allowed_summary)
                                .setTitle(R.string.sync_status_not_allowed)
                                .setPositiveButton(R.string.ok, null);
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    } else startActivity(AccountSyncActivity.createIntent(this, account));
                } else startActivity(TutorialActivity.createIntent(this));
                break;
            case SESSIONS:
                if (accountItem.getConnectionSettings().getXToken() != null &&
                        !accountItem.getConnectionSettings().getXToken().isExpired()) {
                    startActivity(ActiveSessionsActivity.createIntent(this, account));
                }
                break;
        }
    }

    private void startAccountSettingsActivity() {
        startActivity(AccountSettingsActivity.createIntent(this, account));
    }

    @Override
    public void onAccountsChanged(Collection<AccountJid> accounts) {
        LogManager.i(LOG_TAG, "onAccountsChanged " + accounts);

        if (accounts.contains(account)) {
            updateTitle();
            updateOptions();
        }
    }

    @Override
    public void onBlockedListChanged(AccountJid account) {
        if (this.account.equals(account)) {
            updateBlockListOption();
        }
    }

    @Override
    public void onVCardReceived() {
        updateTitle();
    }

    @Override
    public void registerVCardFragment(ContactVcardViewerFragment fragment) {}

    @Subscribe(sticky = true)
    @Override
    public void onAuthErrorEvent(AccountErrorEvent accountErrorEvent) {
        LogManager.i(LOG_TAG, "onAuthErrorEvent ");

        if (!isConnectionSettingsAction) {
            super.onAuthErrorEvent(accountErrorEvent);
        }
    }

    private void setScreenWindowSettings() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
                Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Window win = getWindow();
            WindowManager.LayoutParams winParams = win.getAttributes();
            winParams.flags |= WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS;
            win.setAttributes(winParams);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window win = getWindow();
            WindowManager.LayoutParams winParams = win.getAttributes();
            winParams.flags &= ~WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS;
            win.setAttributes(winParams);
            win.setStatusBarColor(Color.TRANSPARENT);
        }
    }

    private void generateQR() {
        RosterContact rosterContact = RosterManager.getInstance().getRosterContact(account, fakeAccountUser);
        Intent intent = QRCodeActivity.createIntent(AccountActivity.this, account);
        String textName = rosterContact != null ? rosterContact.getName() : "";
        intent.putExtra("account_name", textName);
        String textAddress =  account.getFullJid().asBareJid().toString();
        intent.putExtra("account_address", textAddress);
        intent.putExtra("caller", "AccountActivity");
        startActivity(intent);
    }

    private void runColorPickerDialog() {
        AccountColorDialog.newInstance(account).show(getFragmentManager(),
                AccountColorDialog.class.getSimpleName());
        return;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.generate_qrcode:
                generateQR();
                break;
            case R.id.change_color:
                runColorPickerDialog();
                break;
            default:
                break;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch(menuItem.getItemId()) {
            case R.id.action_generate_qrcode:
                generateQR();
                return true;
            case R.id.action_account_color:
                runColorPickerDialog();
                return true;
            default:
                return super.onOptionsItemSelected(menuItem);
        }
    }
    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        return onOptionsItemSelected(menuItem);
    }
}

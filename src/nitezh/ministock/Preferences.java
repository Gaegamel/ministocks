/*
 The MIT License

 Copyright (c) 2013 Nitesh Patel http://niteshpatel.github.io/ministocks

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.
 */

package nitezh.ministock;

import android.app.SearchManager;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.*;
import android.preference.Preference.OnPreferenceClickListener;
import android.widget.TimePicker;
import nitezh.ministock.widget.WidgetBase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

public class Preferences extends PreferenceActivity
        implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    // Constants
    public static final int STRING_TYPE = 0;
    public static final int LIST_TYPE = 1;
    public static final int CHECKBOX_TYPE = 2;
    // Public variables
    public static int mAppWidgetId = 0;
    // Private
    protected static boolean mStocksDirty = false;
    protected static String mSymbolSearchKey = "";
    private final String CHANGE_LOG =
            "• Fix issue with NASDAQ stock symbol.<br /><br />• Add option for larger widget font.<br /><br />• Other minor bug-fixes.<br /><br /><i>If you appreciate this app please rate it 5 stars in the Android market!</i>";
    // Fields for time pickers
    protected TimePickerDialog.OnTimeSetListener mTimeSetListener;
    protected String mTimePickerKey = null;
    protected int mHour = 0;
    protected int mMinute = 0;

    protected String getChangeLog() {
        return CHANGE_LOG;
    }

    @Override
    public void onNewIntent(Intent intent) {

        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            setPreference(
                    mSymbolSearchKey,
                    intent.getDataString(),
                    intent.getStringExtra(SearchManager.EXTRA_DATA_KEY));

        } else if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            startSearch(query, false, null, false);

        } else if (Intent.ACTION_EDIT.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            startSearch(query, false, null, false);
        }
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {

        // Each widget has its own set of preferences
        return super.getSharedPreferences(name + mAppWidgetId, mode);
    }

    public SharedPreferences getAppPrefs() {

        // Convenience method to get global preferences
        return super.getSharedPreferences(getString(R.string.prefs_name), 0);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Unregister the listener whenever a key changes
        getPreferenceScreen()
                .getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    private void removePref(PreferenceScreen screen, String name) {
        try {
            screen.removePreference(findPreference(name));

        } catch (Exception e) {
        }
    }

    private void removePref(String screenName, String name) {
        PreferenceScreen screen = (PreferenceScreen) findPreference(screenName);
        try {
            screen.removePreference(findPreference(name));

        } catch (Exception e) {
        }
    }

    private void showRecentChanges() {

        // Return if the change log has already been viewed
        if (getAppPrefs()
                .getString("change_log_viewed", "")
                .equals(Utils.BUILD)) {
            return;
        }

        // Cleanup preferences files
        UserData.cleanupPreferenceFiles(getApplicationContext());

        @SuppressWarnings("rawtypes")
        Callable callable = new Callable() {

            @Override
            public Object call() throws Exception {

                // Ensure we don't show this again
                SharedPreferences prefs = getAppPrefs();
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("change_log_viewed", Utils.BUILD);

                // Set first install if not set
                if (prefs.getString("install_date", "").equals("")) {
                    editor.putString("install_date", new SimpleDateFormat(
                            "yyyyMMdd").format(new Date()).toUpperCase());
                }
                editor.commit();

                return new Object();
            }
        };

        Tools.alertWithCallback(
                this,
                "BUILD " + Utils.BUILD,
                getChangeLog(),
                "Close",
                null,
                callable);
    }

    @Override
    protected void onResume() {
        super.onResume();

        showRecentChanges();

        PreferenceScreen screen = getPreferenceScreen();
        SharedPreferences sharedPreferences = screen.getSharedPreferences();

        // Add this widgetId if we don't have it
        Set<Integer> appWidgetIds = new HashSet<Integer>();
        for (int i : UserData.getAppWidgetIds2(getBaseContext()))
            appWidgetIds.add(i);

        if (!appWidgetIds.contains(mAppWidgetId))
            UserData.addAppWidgetId(getBaseContext(), mAppWidgetId, null);

        // Hide preferences for certain widget sizes
        int widgetSize = sharedPreferences.getInt("widgetSize", 0);

        // Remove extra stocks
        if (widgetSize == 0 || widgetSize == 1) {
            PreferenceScreen stock_setup =
                    (PreferenceScreen) findPreference("stock_setup");
            for (int i = 5; i < 11; i++)
                removePref(stock_setup, "Stock" + i);
        }

        // Remove extra widget views
        if (widgetSize == 1 || widgetSize == 3) {
            PreferenceScreen widget_views =
                    (PreferenceScreen) findPreference("widget_views");
            removePref(widget_views, "show_percent_change");
            removePref(widget_views, "show_portfolio_change");
            removePref(widget_views, "show_profit_daily_change");
            removePref(widget_views, "show_profit_change");
        }

        // Hide Feedback option if not relevant
        String install_date = getAppPrefs().getString("install_date", null);
        if (Tools.elapsedDays(install_date, "yyyyMMdd") < 30)
            removePref("about_menu", "rate_app");

        // Initialise the summaries when the preferences screen loads
        Map<String, ?> map = sharedPreferences.getAll();
        for (String key : map.keySet())
            updateSummaries(sharedPreferences, key);

        // Update version number
        findPreference("version").setSummary("BUILD " + Utils.BUILD);

        // Force update of global preferences
        // TODO Ensure the items below are included in the above list
        // rather than updating these items twice (potentially)
        updateSummaries(sharedPreferences, "update_interval");
        updateSummaries(sharedPreferences, "update_start");
        updateSummaries(sharedPreferences, "update_end");
        updateSummaries(sharedPreferences, "update_weekend");

        // Set up a listener whenever a key changes
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(
            SharedPreferences sharedPreferences,
            String key) {

        // Perform some custom handling of some values
        if (key.startsWith("Stock") && !key.endsWith("_summary")) {
            updateStockValue(sharedPreferences, key);

            // Mark stock changed as dirty
            mStocksDirty = true;

        } else if (key.equals("update_interval")) {
            updateGlobalPref(sharedPreferences, key, LIST_TYPE);

            // Warning massage if necessary
            if (sharedPreferences.getString(key, "").equals("900000")
                    || sharedPreferences.getString(key, "").equals("300000")) {

                String title = "Short update interval";
                String body =
                        "Note that choosing a short update interval may drain your battery faster.";
                Tools.showSimpleDialog(this, title, body);
            }

        } else if (key.equals("update_start") || key.equals("update_end")) {
            updateGlobalPref(sharedPreferences, key, STRING_TYPE);

        } else if (key.equals("update_weekend")) {
            updateGlobalPref(sharedPreferences, key, CHECKBOX_TYPE);
        }

        // Update the summary whenever the preference is changed
        updateSummaries(sharedPreferences, key);
    }

    public void
    updateStockValue(SharedPreferences sharedPreferences, String key) {

        // Unregister the listener whenever a key changes
        getPreferenceScreen()
                .getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);

        // Massages the value: remove whitespace and upper-case
        String value = sharedPreferences.getString(key, "");
        value = value.replace(" ", "");
        value = value.toUpperCase();

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(key, value);
        editor.commit();

        // Also update the UI
        EditTextPreference preference =
                (EditTextPreference) findPreference(key);
        preference.setText(value);

        // Set up a listener whenever a key changes
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    public void updateFromGlobal(
            SharedPreferences sharedPreferences,
            String key,
            int valType) {

        // Unregister the listener whenever a key changes
        getPreferenceScreen()
                .getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);

        // Update the widget prefs with the interval
        SharedPreferences.Editor editor = sharedPreferences.edit();

        if (valType == STRING_TYPE) {
            String value = getAppPrefs().getString(key, "");
            if (!value.equals("")) {
                editor.putString(key, value);
            }

        } else if (valType == LIST_TYPE) {
            String value = getAppPrefs().getString(key, "");
            if (!value.equals("")) {
                editor.putString(key, value);
                ((ListPreference) findPreference(key)).setValue(value);
            }

        } else if (valType == CHECKBOX_TYPE) {
            Boolean value = getAppPrefs().getBoolean(key, false);
            editor.putBoolean(key, value);
            ((CheckBoxPreference) findPreference(key)).setChecked(value);
        }
        editor.commit();

        // Set up a listener whenever a key changes
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    public void updateGlobalPref(
            SharedPreferences sharedPreferences,
            String key,
            int valType) {

        // Unregister the listener whenever a key changes
        getPreferenceScreen()
                .getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);

        // Update the global preferences with the widget update interval
        SharedPreferences.Editor editor = getAppPrefs().edit();

        if (valType == STRING_TYPE || valType == LIST_TYPE)
            editor.putString(key, sharedPreferences.getString(key, ""));

        else if (valType == CHECKBOX_TYPE)
            editor.putBoolean(key, sharedPreferences.getBoolean(key, false));

        editor.commit();

        // Set up a listener whenever a key changes
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    protected void showDisclaimer() {
        String title = "Disclaimer";
        String body =
                "Copyright © 2011 Nitesh Patel<br/><br />All rights reserved.<br /><br /> THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS \"AS IS\" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.";
        Tools.showSimpleDialog(this, title, body);
    }

    protected void showHelp() {
        String title = "Entering stocks";
        String body =
                "<b>Entering stock symbols</b><br/><br />Stock symbols must be in the Yahoo format, which you can look up on the Yahoo Finance website.";
        Tools.showSimpleDialog(this, title, body);
    }

    protected void showHelpPrices() {
        String title = "Updating prices";
        String body =
                "You can set how often, and when the widget updates in the Advanced settings menu.  The setting applies globally to all the widgets.<br /><br />Stock price information is provided by Yahoo Finance, and there may be a delay (from real-time prices, to up to 30 mins) for some exchanges.<br /><br />Note that the time in the lower-left of the widget is the time that the data was retrieved from Yahoo, not the time of the live price.<br /><br />If an internet connection is not present when an update occurs, the widget will just use the last shown data, and the time for that data.<br /><br /><b>Update prices now feature</b><br /><br />This will update the prices in all your widgets, if there is an internet connection available.";
        Tools.showSimpleDialog(this, title, body);
    }

    protected void showTimePickerDialog(
            Preference preference,
            String defaultValue) {
        // Get the raw value from the preferences
        String value =
                preference.getSharedPreferences().getString(
                        preference.getKey(),
                        defaultValue);

        mHour = 0;
        mMinute = 0;

        if (value != null && !value.equals("")) {
            String[] items = value.split(":");
            mHour = Integer.parseInt(items[0]);
            mMinute = Integer.parseInt(items[1]);
        }

        mTimePickerKey = preference.getKey();
        new TimePickerDialog(this, mTimeSetListener, mHour, mMinute, true)
                .show();
    }

    @Override
    protected void
    onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != 1) {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    public void setTimePickerPreference(int hourOfDay, int minute) {

        // Set the preference value
        SharedPreferences prefs = getPreferenceScreen().getSharedPreferences();

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(mTimePickerKey, String.valueOf(hourOfDay)
                + ":"
                + String.valueOf(minute));
        editor.commit();

        // Also update the UI
        updateSummaries(
                getPreferenceScreen().getSharedPreferences(),
                mTimePickerKey);
    }

    public void setPreference(String key, String value, String summary) {

        // Return if no key
        if (key.equals("")) {
            return;
        }

        // Set the stock value
        SharedPreferences prefs = getPreferenceScreen().getSharedPreferences();

        // Ignore the remove and manual entry options
        if (value.endsWith("and close")) {
            value = "";

        } else if (value.startsWith("Use ")) {
            value = value.replace("Use ", "");
        }

        // Set dirty
        mStocksDirty = true;

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(key, value);
        editor.putString(key + "_summary", summary);
        editor.commit();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        // Hook the About preference to the About (Ministocks) activity
        Preference about = findPreference("about");
        about.setOnPreferenceClickListener(new OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {
                showDisclaimer();
                return true;
            }
        });

        // Hook up the help preferences
        Preference help = findPreference("help");
        help.setOnPreferenceClickListener(new OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {
                showHelp();
                return true;
            }
        });

        // Hook up the symbol search for the stock preferences
        for (int i = 1; i < 11; i++) {
            String key = "Stock" + i;
            findPreference(key).setOnPreferenceClickListener(
                    new OnPreferenceClickListener() {

                        @Override
                        public boolean onPreferenceClick(Preference preference) {
                            mSymbolSearchKey = preference.getKey();

                            // Start search with current value as query
                            String query =
                                    preference
                                            .getSharedPreferences()
                                            .getString(mSymbolSearchKey, "");
                            startSearch(query, false, null, false);

                            return true;
                        }
                    });
        }

        // Hook up the help preferences
        Preference help_usage = findPreference("help_usage");
        help_usage
                .setOnPreferenceClickListener(new OnPreferenceClickListener() {

                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showHelpUsage();
                        return true;
                    }
                });

        // Hook the Help preference to the Help activity
        Preference help_portfolio = findPreference("help_portfolio");
        help_portfolio
                .setOnPreferenceClickListener(new OnPreferenceClickListener() {

                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showHelpPortfolio();
                        return true;
                    }
                });

        // Hook the Help preference to the Help activity
        Preference help_prices = findPreference("help_prices");
        help_prices
                .setOnPreferenceClickListener(new OnPreferenceClickListener() {

                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showHelpPrices();
                        return true;
                    }
                });

        // Hook the Update preference to the Help activity
        Preference updateNow = findPreference("update_now");
        updateNow.setOnPreferenceClickListener(new OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {

                // Update all widgets and quit
                WidgetBase.updateWidgets(
                        getApplicationContext(),
                        WidgetBase.VIEW_UPDATE);

                finish();
                return true;
            }
        });

        // Hook the Portfolio preference to the Portfolio activity
        Preference portfolio = findPreference("portfolio");
        portfolio.setOnPreferenceClickListener(new OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(Preferences.this, Portfolio.class);
                startActivity(intent);
                return true;
            }
        });

		/*
         * // Hook the Backup portfolio option to the backup portfolio method
		 * Preference backup = findPreference("backup_portfolio");
		 * backup.setOnPreferenceClickListener(new OnPreferenceClickListener() {
		 * 
		 * @Override public boolean onPreferenceClick(Preference preference) {
		 * UserData.backupPortfolio(getApplicationContext());
		 * 
		 * Intent intent = new Intent(Preferences.this, Portfolio.class);
		 * startActivity(intent); return true; } }); // Hook the Restore
		 * portfolio option to the restore portfolio method Preference restore =
		 * findPreference("restore_portfolio");
		 * restore.setOnPreferenceClickListener(new OnPreferenceClickListener()
		 * {
		 * 
		 * @Override public boolean onPreferenceClick(Preference preference) {
		 * UserData.restorePortfolio(getApplicationContext());
		 * 
		 * Intent intent = new Intent(Preferences.this, Portfolio.class);
		 * startActivity(intent); return true; } });
		 */

        // Hook Rate Ministocks preference to the market link
        Preference rate_app = findPreference("rate_app");
        rate_app.setOnPreferenceClickListener(new OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {
                showFeedbackOption();
                return true;
            }
        });

        // Hook the Feedback preference to the Portfolio activity
        Preference feedback = findPreference("feedback");
        feedback.setOnPreferenceClickListener(new OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {

                // Open the e-mail client with destination and subject
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("message/rfc822");

                String[] to_addr = {"nitezh@gmail.com"};
                intent.putExtra(Intent.EXTRA_EMAIL, to_addr);
                intent.putExtra(
                        Intent.EXTRA_SUBJECT,
                        getString(R.string.app_name) + " BUILD " + Utils.BUILD);
                intent.setType("message/rfc822");

                // In case we can't launch e-mail, show a dialog
                try {
                    startActivity(intent);
                    return true;

                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();

                } catch (Throwable e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

                // Show dialog if launching e-mail fails
                Tools
                        .showSimpleDialog(
                                getApplicationContext(),
                                "Launching e-mail failed",
                                "We were unable to launch your e-mail client automatically.<br /><br />Our e-mail address for support and feedback is nitezh@gmail.com");
                return true;
            }
        });

        // Hook the Change history preference to the Change history dialog
        Preference change_history = findPreference("change_history");
        change_history
                .setOnPreferenceClickListener(new OnPreferenceClickListener() {

                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showChangeLog();
                        return true;
                    }
                });

        // Callback received when the user sets the time in the dialog
        mTimeSetListener = new

                TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                        setTimePickerPreference(hourOfDay, minute);
                    }
                };

        // Hook the Update schedule preferences up
        Preference update_start = findPreference("update_start");
        update_start
                .setOnPreferenceClickListener(new OnPreferenceClickListener() {

                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showTimePickerDialog(preference, "00:00");
                        return true;
                    }
                });
        Preference update_end = findPreference("update_end");
        update_end
                .setOnPreferenceClickListener(new OnPreferenceClickListener() {

                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showTimePickerDialog(preference, "23:59");
                        return true;
                    }
                });
    }

    public void updateSummaries(SharedPreferences sharedPreferences, String key) {

        // Initialise the Stock summaries
        if (key.startsWith("Stock") && !key.endsWith("_summary")) {

            // Update the summary based on the stock value
            String value = sharedPreferences.getString(key, "");
            String summary = sharedPreferences.getString(key + "_summary", "");

            // Set the title
            if (value.equals("")) {
                value = key.replace("Stock", "Stock ");
                summary = "Set symbol";
            }

            // Set the summary appropriately
            else if (summary.equals("")) {
                summary = "No description";
            }

            findPreference(key).setTitle(value);
            findPreference(key).setSummary(summary);

            // Initialise the ListPreference summaries
        } else if (key.startsWith("background")
                || key.startsWith("updated_colour")
                || key.startsWith("updated_display")
                || key.startsWith("text_style")) {

            String value = sharedPreferences.getString(key, "");
            findPreference(key).setSummary(
                    "Selected: "
                            + value.substring(0, 1).toUpperCase()
                            + value.substring(1));

        }

        // Initialise the Update interval
        else if (key.startsWith("update_interval")) {

            // Update summary based on selected value
            String displayValue = "30 minutes";
            String value = getAppPrefs().getString(key, "1800000");
            if (value.equals("300000")) {
                displayValue = "5 minutes";
            } else if (value.equals("900000")) {
                displayValue = "15 minutes";
            } else if (value.equals("1800000")) {
                displayValue = "30 minutes";
            } else if (value.equals("3600000")) {
                displayValue = "One hour";
            } else if (value.equals("10800000")) {
                displayValue = "Three hours";
            } else if (value.equals("86400000")) {
                displayValue = "Daily";
            }
            findPreference(key).setSummary("Selected: " + displayValue);

            // Update the value of the update interval
            updateFromGlobal(sharedPreferences, "update_interval", LIST_TYPE);
        }

        // Update time picker summaries
        else if (key.equals("update_start") || key.equals("update_end")) {
            String value = getAppPrefs().getString(key, null);

            mHour = 0;
            mMinute = 0;

            if (value != null) {
                String[] items = value.split(":");
                mHour = Integer.parseInt(items[0]);
                mMinute = Integer.parseInt(items[1]);
            }

            findPreference(key).setSummary(
                    "Time set: "
                            + Tools.timeDigitPad(mHour)
                            + ":"
                            + Tools.timeDigitPad(mMinute));

            // Update the value of the update limits
            updateFromGlobal(sharedPreferences, key, STRING_TYPE);
        } else if (key.equals("update_weekend")) {
            updateFromGlobal(sharedPreferences, key, CHECKBOX_TYPE);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Update the widget when we quit the preferences, and if the dirty,
        // flag is true then do a web update, otherwise do a regular update
        if (mStocksDirty) {
            mStocksDirty = false;
            WidgetBase.updateWidgets(
                    getApplicationContext(),
                    WidgetBase.VIEW_UPDATE);

        } else {
            WidgetBase.update(
                    getApplicationContext(),
                    mAppWidgetId,
                    WidgetBase.VIEW_NO_UPDATE);
        }
        finish();
    }

    private void showHelpUsage() {
        String title = "Selecting widget views";
        String body =
                "The widget has multiple views that display different information.<br /><br />These views can be turned on from the Widget views menu in settings.<br /><br />Once selected, the views can be changed on your homescreen by touching the right-side of the widget.<br /><br />If a stock does not have information for a particular view, then the daily percentage change will instead be displayed for that stock in blue.<br /><br /><b>Daily change %</b><br /><br />Shows the current stock price with the daily percentage change.<br /><br /><b>Daily change (DA)</b><br /><br />Shows the current stock price with the daily price change.<br /><br /><b>Total change % (PF T%)</b><br /><br />Shows the current stock price with the total percentage change from the buy price in the portfolio.<br /><br /><b>Total change (PF TA)</b><br /><br />Shows the current stock price with the total price change from the buy price in the portfolio.<br /><br /><b>Total change AER % (PF AER)</b><br /><br />Shows the current stock price with the annualised percentage change using the buy price in the portfolio.<br /><br /><b>P/L daily change % (P/L D%)</b><br /><br />Shows your current holding value with the daily percentage change.<br /><br /><b>P/L daily change (P/L DA)</b><br /><br />Shows your current holding value with the daily price change.<br /><br /><b>P/L total change % (P/L T%)</b><br /><br />Shows your current holding value with the total percentage change from the buy cost in the portfolio.<br /><br /><b>P/L total change (P/L TA)</b><br /><br />Shows your current holding value with the total value change from the buy cost in the portfolio.<br /><br /><b>P/L total change AER (P/L AER)</b><br /><br />Shows your current holding value with the annualised percentage change using the buy cost in the portfolio.";
        Tools.showSimpleDialog(this, title, body);
    }

    private void showHelpPortfolio() {
        String title = "Using the portfolio";
        String body =
                "On the portfolio screen you will see all the stocks that you have entered in your widgets in one list.<br /><br />You can touch an item to enter your stock purchase details.<br /><br /><b>Entering purchase details</b><br /><br />Enter the price that you bought the stock for, this will then be used for the Portfolio and Profit and loss widget views.<br /><br />The Date is optional, and will be used for the AER rate on the portfolio AER and profit and loss AER views.<br /><br />The Quantity is optional, and will be used to calculate your holdings for the profit and loss views.  You may use negative values to simulate a short position.<br /><br />The High price limit and Low price limit are optional.  When the current price hits these limits, the price color will change in the widget.<br /><br /><b>Removing purchase details</b><br /><br />To remove purchase and alert details, long press the portfolio item and then choose the Clear details option.";
        Tools.showSimpleDialog(this, title, body);
    }

    private void showChangeLog() {
        String title = "BUILD " + Utils.BUILD;
        String body = CHANGE_LOG;
        Tools.showSimpleDialog(this, title, body);
    }

    private void showFeedbackOption() {

        @SuppressWarnings("rawtypes")
        Callable callable = new Callable() {

            @Override
            public Object call() throws Exception {
                startActivity(new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=nitezh.ministock")));

                return new Object();
            }
        };

        Tools
                .alertWithCallback(
                        this,
                        "Rate Ministocks",
                        "Please support Ministocks by giving the application a 5 star rating in the android market.<br /><br />Motivation to continue to improve the product and add new features comes from positive feedback and ratings.",
                        "Rate it!",
                        "Close",
                        callable);
    }

}
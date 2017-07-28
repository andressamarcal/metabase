/* @flow weak */

import 'babel-polyfill';
import 'number-to-locale-string';

import React from 'react'
import ReactDOM from 'react-dom'
import { Provider } from 'react-redux'

import MetabaseAnalytics, { registerAnalyticsClickListener } from "metabase/lib/analytics";
import MetabaseSettings from "metabase/lib/settings";

import api from "metabase/lib/api";

import { getStore } from './store'

import { refreshSiteSettings } from "metabase/redux/settings";

import { Router, useRouterHistory } from "react-router";
import { createHistory } from 'history'
import { syncHistoryWithStore } from 'react-router-redux';

// remove trailing slash
const BASENAME = window.MetabaseRoot.replace(/\/+$/, "");

api.basename = BASENAME;

const browserHistory = useRouterHistory(createHistory)({
    basename: BASENAME
});

function _init(reducers, getRoutes, callback) {
    const store = getStore(reducers, browserHistory);
    const routes = getRoutes(store);
    const history = syncHistoryWithStore(browserHistory, store);

    ReactDOM.render(
        <Provider store={store}>
          <Router history={history}>
            {routes}
          </Router>
        </Provider>
    , document.getElementById('root'));

    // listen for location changes and use that as a trigger for page view tracking
    history.listen(location => {
        MetabaseAnalytics.trackPageView(location.pathname);
    });

    registerAnalyticsClickListener();

    store.dispatch(refreshSiteSettings());

    // enable / disable GA based on opt-out of anonymous tracking
    MetabaseSettings.on("anon_tracking_enabled", () => {
        window['ga-disable-' + MetabaseSettings.get('ga_code')] = MetabaseSettings.isTrackingEnabled() ? null : true;
    });

    MetabaseSettings.on("color_scheme", updateColorScheme);
    updateColorScheme()

    if (callback) {
        callback(store);
    }
}

export function init(...args) {
    if (document.readyState != 'loading') {
        _init(...args);
    } else {
        document.addEventListener('DOMContentLoaded', () => _init(...args));
    }
}

let BRAND_COLOR_REGEX = /rgb\(80, 158, 227\)|rgb\(74, 144, 226\)|rgb\(154, 167, 188\)/g;
function updateColorScheme() {
    const colorScheme = MetabaseSettings.colorScheme();
    for (const sheet of document.styleSheets) {
        for (const rule of sheet.cssRules || sheet.rules || []) {
            if (BRAND_COLOR_REGEX.test(rule.cssText) && rule.style) {
                for (const [prop, value] of Object.entries(rule.style)) {
                    if (BRAND_COLOR_REGEX.test(value)) {
                        rule.style[prop] = value.replace(BRAND_COLOR_REGEX, colorScheme)
                    }
                }
            }
        }
    }
}

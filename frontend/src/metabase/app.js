/* @flow weak */

import "babel-polyfill";

// Use of classList.add and .remove in Background and FitViewPort Hocs requires
// this polyfill so that those work in older browsers
import "classlist-polyfill";

import "number-to-locale-string";

// If enabled this monkeypatches `t` and `jt` to return blacked out
// strings/elements to assist in finding untranslated strings.
import "metabase/lib/i18n-debug";

// set the locale before loading anything else
import "metabase/lib/i18n";

// NOTE: why do we need to load this here?
import "metabase/lib/colors";

import { updateColors, updateColorsJS } from "metabase/lib/whitelabel";
// Update the JS colors to ensure components that use a color statically get the
// whitelabeled color (though this doesn't help if the admin changes a color and
// doesn't refresh)
// Don't update CSS colors yet since all the CSS hasn't been loaded yet
updateColorsJS();

import React from "react";
import ReactDOM from "react-dom";
import { Provider } from "react-redux";
import { ThemeProvider } from "styled-components";

import MetabaseAnalytics, {
  registerAnalyticsClickListener,
} from "metabase/lib/analytics";
import MetabaseSettings from "metabase/lib/settings";

import api from "metabase/lib/api";
import { IFRAMED } from "metabase/lib/dom";

import { getStore } from "./store";

import { refreshSiteSettings } from "metabase/redux/settings";

// router
import { Router, useRouterHistory } from "react-router";
import { createHistory } from "history";
import { syncHistoryWithStore } from "react-router-redux";

// drag and drop
import HTML5Backend from "react-dnd-html5-backend";
import { DragDropContextProvider } from "react-dnd";

import _ from "underscore";

// remove trailing slash
const BASENAME = window.MetabaseRoot.replace(/\/+$/, "");

api.basename = BASENAME;

const browserHistory = useRouterHistory(createHistory)({
  basename: BASENAME,
});

const theme = {
  space: [4, 8, 16, 32, 64, 128],
};

function _init(reducers, getRoutes, callback) {
  const store = getStore(reducers, browserHistory);
  const routes = getRoutes(store);
  const history = syncHistoryWithStore(browserHistory, store);

  let root;
  ReactDOM.render(
    <Provider store={store} ref={ref => (root = ref)}>
      <DragDropContextProvider backend={HTML5Backend} context={{ window }}>
        <ThemeProvider theme={theme}>
          <Router history={history}>{routes}</Router>
        </ThemeProvider>
      </DragDropContextProvider>
    </Provider>,
    document.getElementById("root"),
  );

  // listen for location changes and use that as a trigger for page view tracking
  history.listen(location => {
    MetabaseAnalytics.trackPageView(location.pathname);
  });

  if (IFRAMED) {
    let currentHref;
    // NOTE: history.listen and window's onhashchange + popstate events were not
    // enough to catch all URL changes, so just poll for now :(
    setInterval(() => {
      if (currentHref !== window.location.href) {
        // FIXME SECURITY: use whitelisted origin instead of "*"
        window.parent.postMessage(
          {
            metabase: {
              type: "location",
              // extract just the string properties from window.location
              location: _.pick(window.location, v => typeof v === "string"),
            },
          },
          "*",
        );
        currentHref = window.location.href;
      }
    }, 100);
  }

  registerAnalyticsClickListener();

  store.dispatch(refreshSiteSettings());

  // enable / disable GA based on opt-out of anonymous tracking
  MetabaseSettings.on("anon_tracking_enabled", () => {
    window[
      "ga-disable-" + MetabaseSettings.get("ga_code")
    ] = MetabaseSettings.isTrackingEnabled() ? null : true;
  });

  MetabaseSettings.on("application-colors", updateColors);
  MetabaseSettings.on("application-colors", () => {
    root.forceUpdate();
  });
  updateColors();

  window.Metabase = window.Metabase || {};
  window.Metabase.store = store;

  if (callback) {
    callback(store);
  }
}

export function init(...args) {
  if (document.readyState != "loading") {
    _init(...args);
  } else {
    document.addEventListener("DOMContentLoaded", () => _init(...args));
  }
}

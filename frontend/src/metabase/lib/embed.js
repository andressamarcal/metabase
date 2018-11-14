import { push } from "react-router-redux";

import _ from "underscore";

import { IFRAMED, IFRAMED_IN_SELF } from "./dom";

// detect if this page is embedded in itself, i.e. it's a embed preview
// will need to do something different if we ever embed metabase in itself for another reason
export const IS_EMBED_PREVIEW = IFRAMED_IN_SELF;

export function initializeEmbedding(store) {
  if (IFRAMED) {
    let currentHref;
    // NOTE: history.listen and window's onhashchange + popstate events were not
    // enough to catch all URL changes, so just poll for now :(
    setInterval(() => {
      if (currentHref !== window.location.href) {
        window.parent.postMessage(
          {
            metabase: {
              type: "location",
              // extract just the string properties from window.location
              location: _.pick(window.location, v => typeof v === "string"),
            },
          },
          // FIXME SECURITY: use whitelisted origin instead of "*"
          "*",
        );
        currentHref = window.location.href;
      }
    }, 100);
    window.addEventListener("message", e => {
      if (e.source === window.parent && e.data.metabase) {
        console.log(e.data.metabase);
        if (e.data.metabase.type === "location") {
          store.dispatch(push(e.data.metabase.location));
        }
      }
    });
  }
}

import { open } from "metabase/lib/dom";

import _ from "underscore";

export function performAction(action, { dispatch, onChangeCardAndRun }) {
  if (action.action) {
    const reduxAction = action.action();
    if (reduxAction) {
      // $FlowFixMe: dispatch provided by @connect
      dispatch(reduxAction);
      return true;
    }
  }
  if (action.url) {
    const url = action.url();
    if (url) {
      open(url);
      return true;
    }
  }
  if (action.question) {
    const question = action.question();
    if (question) {
      onChangeCardAndRun({ nextCard: question.card() });
      return true;
    }
  }
  return false;
}

export function performDefaultAction(actions, props) {
  // "default" action if there's only one
  // NOTE: disabled for now, always show action menu even if there's a single default action
  // if (actions.length === 1 && actions[0].default) {
  //   return performAction(actions[0], props);
  // }

  // "defaultAlways" action even if there's more than one
  const action = _.find(actions, action => action.defaultAlways);
  if (action) {
    return performAction(action, props);
  }

  return false;
}

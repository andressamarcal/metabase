export const PLUGIN_USER_FORM_FIELDS = [];

const register = {
  userFormField: plugin => PLUGIN_USER_FORM_FIELDS.push(plugin),
};

import mt from "./mt";
mt(register);

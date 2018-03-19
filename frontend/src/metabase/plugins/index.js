export const EDIT_USER_FORM_PLUGINS = [];

const register = {
  editUserForm: plugin => EDIT_USER_FORM_PLUGINS.push(plugin),
};

import mt from "./mt";
mt(register);

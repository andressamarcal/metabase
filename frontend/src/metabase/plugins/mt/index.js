import LoginAttributesWidget from "./components/LoginAttributesWidget";

export default register => {
  register.userFormField({
    name: "login_attributes",
    title: "Attributes",
    type: LoginAttributesWidget,
  });
};

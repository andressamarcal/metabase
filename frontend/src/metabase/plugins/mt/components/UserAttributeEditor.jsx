import React from "react";

import Button from "metabase/components/Button";

const UserAttributeEditor = ({ user, onChange }) => {
  const attributes = user.login_attributes || {};
  return (
    <div>
      <h3 className="pb1">{user.first_name}'s attributes</h3>
      <table>
        {Object.entries(attributes).map(([name, value], index) => (
          <tr key={index}>
            <td className="pb1">
              <input
                className="input"
                value={name}
                placeholder="Name"
                onChange={e =>
                  onChange({
                    ...user,
                    login_attributes: replaceAttributeName(
                      attributes,
                      name,
                      e.target.value,
                    ),
                  })
                }
              />
            </td>
            <td className="pb1">
              <input
                className="input"
                value={value}
                placeholder="Value"
                onChange={e =>
                  onChange({
                    ...user,
                    login_attributes: replaceAttributeValue(
                      attributes,
                      name,
                      e.target.value,
                    ),
                  })
                }
              />
            </td>
            <td>
              <Button
                icon="close"
                borderless
                onClick={() =>
                  onChange({
                    ...user,
                    login_attributes: removeAttribute(attributes, name),
                  })
                }
              />
            </td>
          </tr>
        ))}
      </table>
      <Button
        icon="add"
        type="button" // prevent submit. should be the default but it's not
        borderless
        className="text-brand p0 py1"
        onClick={() =>
          onChange({ ...user, login_attributes: addAttribute(attributes) })
        }
      >
        Add an attribute
      </Button>
    </div>
  );
};

const addAttribute = attributes => {
  return { ...attributes, "": "" };
};

const removeAttribute = (attributes, prevName) => {
  attributes = { ...attributes };
  delete attributes[prevName];
  return attributes;
};

const replaceAttributeValue = (attributes, oldName, newValue) => {
  return { ...attributes, [oldName]: newValue };
};

const replaceAttributeName = (attributes, oldName, newName) => {
  const newAttributes = {};
  for (const name in attributes) {
    newAttributes[name === oldName ? newName : name] = attributes[name];
  }
  return newAttributes;
};

export default UserAttributeEditor;

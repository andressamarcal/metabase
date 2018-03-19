import React from "react";

import Button from "metabase/components/Button";

const UserAttributeEditor = ({ user, onChange }) => {
  const attributes = user.attributes || [];
  return (
    <div>
      <h3 className="pb1">{user.first_name}'s attributes</h3>
      <table>
        {attributes.map(({ name, value }, index) => (
          <tr>
            <td className="pb1">
              <input
                className="input"
                value={name}
                placeholder="Name"
                onChange={e =>
                  onChange({
                    ...user,
                    attributes: [
                      ...attributes.slice(0, index),
                      { name: e.target.value, value: value },
                      ...attributes.slice(index + 1),
                    ],
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
                    attributes: [
                      ...attributes.slice(0, index),
                      { name: name, value: e.target.value },
                      ...attributes.slice(index + 1),
                    ],
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
                    attributes: [
                      ...attributes.slice(0, index),
                      ...attributes.slice(index + 1),
                    ],
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
          onChange({
            ...user,
            attributes: (user.attributes || []).concat({ name: "", value: "" }),
          })
        }
      >
        Add an attribute
      </Button>
    </div>
  );
};

export default UserAttributeEditor;

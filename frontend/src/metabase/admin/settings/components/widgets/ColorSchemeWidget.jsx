import React from "react";

import ColorPicker from "metabase/components/ColorPicker";
import Button from "metabase/components/Button";
import Icon from "metabase/components/Icon";
import _ from "underscore";
import { capitalize } from "metabase/lib/formatting";

import { originalColors } from "metabase/lib/whitelabel";

const ColorSchemeWidget = ({ setting, onChange }) => {
  const value = setting.value || {};
  const colors = { ...originalColors, ...value };

  return (
    <div>
      <table>
        <tbody>
          {Object.entries(colors).map(([name, color]) => (
            <tr>
              <td>
                {name
                  .replace("bg", "background")
                  .replace(/\d+/, match => " " + match)
                  .split("-")
                  .map(capitalize)
                  .join(" ")}:
              </td>
              <td>
                <span className="mx1">
                  <ColorPicker
                    fancy
                    triggerSize={16}
                    value={color}
                    onChange={color => onChange({ ...value, [name]: color })}
                  />
                </span>
                {color !== originalColors[name] && (
                  <Icon
                    name="close"
                    className="text-grey-2 text-grey-4-hover cursor-pointer"
                    onClick={() => onChange({ ...value, [name]: undefined })}
                  />
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="pt1">
        <Button
          onClick={() => onChange({})}
          disabled={Object.keys(value).length === 0}
        >
          Reset
        </Button>
      </div>
    </div>
  );
};

export default ColorSchemeWidget;

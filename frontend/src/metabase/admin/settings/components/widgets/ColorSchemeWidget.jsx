import React from "react";

import ColorPicker from "metabase/components/ColorPicker";
import Button from "metabase/components/Button";
import Icon from "metabase/components/Icon";
import _ from "underscore";
import { humanize } from "metabase/lib/formatting";

import { originalColors } from "metabase/lib/whitelabel";

const THEMEABLE_COLORS = ["brand"].concat(
  Object.keys(originalColors).filter(name => name.startsWith("accent")),
);

const COLOR_DISPLAY_PROPERTIES = {
  brand: {
    name: "Brand",
    description: "Brand is blah blah blah",
  },
};

const ColorSchemeWidget = ({ setting, onChange }) => {
  const value = setting.value || {};
  const colors = { ...originalColors, ...value };

  return (
    <div>
      <table>
        <tbody>
          {THEMEABLE_COLORS.map(name => {
            const properties = COLOR_DISPLAY_PROPERTIES[name] || {};
            return (
              <tr>
                <td>{properties.name || humanize(name)}:</td>
                <td>
                  <span className="mx1">
                    <ColorPicker
                      fancy
                      triggerSize={16}
                      value={colors[name]}
                      onChange={color => onChange({ ...value, [name]: color })}
                    />
                  </span>
                </td>
                <td>
                  {colors[name] !== originalColors[name] && (
                    <Icon
                      name="close"
                      className="text-grey-2 text-grey-4-hover cursor-pointer"
                      onClick={() => onChange({ ...value, [name]: undefined })}
                    />
                  )}
                </td>
                <td>
                  <span className="mx2">{properties.description}</span>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
};

export default ColorSchemeWidget;

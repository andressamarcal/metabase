import React from "react";

import ColorPicker from "metabase/components/ColorPicker";
import Button from "metabase/components/Button";
import Icon from "metabase/components/Icon";
import _ from "underscore";

import { originalColors } from "metabase/lib/whitelabel";

const ColorSchemeWidget = ({ setting, onChange }) => {
  const value = setting.value || {};
  const colors = { ...originalColors, ...value };

  return (
    <div>
      {Object.entries(colors).map(([name, color]) => (
        <div>
          {name}:{" "}
          <ColorPicker
            fancy
            triggerSize={16}
            value={color}
            onChange={color => onChange({ ...value, [name]: color })}
          />
          {color !== originalColors[name] && (
            <Icon
              name="close"
              onClick={() => onChange({ ...value, [name]: undefined })}
            />
          )}
        </div>
      ))}
      <Button
        onClick={() => onChange({})}
        disabled={Object.keys(value).length === 0}
      >
        Reset
      </Button>
    </div>
  );
};

export default ColorSchemeWidget;

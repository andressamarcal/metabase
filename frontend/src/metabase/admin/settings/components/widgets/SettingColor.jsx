import React from "react";

import ColorPicker from "metabase/components/ColorPicker";

const SettingColor = ({ setting, updateSetting }) =>
    <ColorPicker
        value={setting.value}
        onChange={updateSetting}
    />

export default SettingColor;

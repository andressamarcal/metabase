import React from "react";

import Icon from "metabase/components/Icon";
import LogoIcon from "metabase/components/LogoIcon";
import SettingInput from "./SettingInput";

const LogoUpload = ({ setting, onChange, ...props }) => (
  <div>
    <div className="mb1">
      <LogoIcon />
    </div>
    {window.File && window.FileReader ? (
      <input
        type="file"
        onChange={e => {
          if (e.target.files.length > 0) {
            const reader = new FileReader();
            reader.onload = e => onChange(e.target.result);
            reader.readAsDataURL(e.target.files[0]);
          }
        }}
      />
    ) : (
      <SettingInput setting={setting} onChange={onChange} {...props} />
    )}
    <Icon name="close" onClick={() => onChange(undefined)} />
  </div>
);

export default LogoUpload;

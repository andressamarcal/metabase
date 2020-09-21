import React from "react";

import ChartSettingInput from "metabase/visualizations/components/settings/ChartSettingInput";
import PopoverWithTrigger from "metabase/components/PopoverWithTrigger";
import Icon from "metabase/components/Icon";

const ChartSettingInputWithInfo = ({ infoName, infos, ...props }) => (
  <div>
    <ChartSettingInput {...props} />
    <div className="mt1">
      <PopoverWithTrigger
        triggerElement={
          <span className="h4 text-brand cursor-pointer inline-flex align-center">
            <Icon name="info" className="mr1" />
            {infoName}
          </span>
        }
        sizeToFit
      >
        <div className="scroll-y px2 pt2">
          {infos.map(info => (
            <div className="pb2">
              <span className="text-code p1">{info}</span>
            </div>
          ))}
        </div>
      </PopoverWithTrigger>
    </div>
  </div>
);

export default ChartSettingInputWithInfo;

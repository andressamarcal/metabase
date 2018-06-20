import React from "react";
import { DropTarget } from "react-dnd";
import cx from "classnames";

import { PinnableDragTypes } from "./index";

const PIN_DROP_TARGET_INDICATOR_WIDTH = 3;

@DropTarget(
  PinnableDragTypes,
  {
    drop(props, monitor, component) {
      return { pinIndex: props.pinIndex };
    },
  },
  (connect, monitor) => ({
    highlighted: monitor.canDrop(),
    hovered: monitor.isOver() && monitor.canDrop(),
    connectDropTarget: connect.dropTarget(),
  }),
)
export default class PinPositionDropTarget extends React.Component {
  render() {
    const {
      left,
      right,
      connectDropTarget,
      hovered,
      highlighted,
      offset = 0,
    } = this.props;
    return connectDropTarget(
      <div
        className={cx("absolute top bottom", {
          "pointer-events-none": !highlighted,
        })}
        style={{
          width: left | right ? "50%" : undefined,
          left: !right ? 0 : undefined,
          right: !left ? 0 : undefined,
        }}
      >
        <div
          className={cx("absolute", { "bg-brand": hovered })}
          style={{
            top: 10,
            bottom: 10,
            width: PIN_DROP_TARGET_INDICATOR_WIDTH,
            left: !right
              ? -PIN_DROP_TARGET_INDICATOR_WIDTH / 2 - offset
              : undefined,
            right: right
              ? -PIN_DROP_TARGET_INDICATOR_WIDTH / 2 - offset
              : undefined,
          }}
        />
      </div>,
    );
  }
}

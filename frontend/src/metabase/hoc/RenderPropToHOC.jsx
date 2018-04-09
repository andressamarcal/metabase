import React from "react";

export default function renderPropToHoc(RenderPropComponent) {
  return ComposedComponent => props => (
    <RenderPropComponent
      {...props}
      children={childrenProps => (
        <ComposedComponent {...props} {...childrenProps} />
      )}
    />
  );
}

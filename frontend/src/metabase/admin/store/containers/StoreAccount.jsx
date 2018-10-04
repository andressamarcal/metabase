import React from "react";
import { Box, Flex } from "grid-styled";
import { t } from "c-3po";
import { connect } from "react-redux";

import colors from "metabase/lib/colors";

import StoreIcon from "../components/StoreIcon";
import Card from "metabase/components/Card";

import fitViewport from "metabase/hoc/FitViewPort";

import FEATURES from "../lib/features";

@fitViewport
@connect(state => {
  const features = state.settings.values["premium_features"];
  const featuresEnabled = Object.keys(features).filter(
    f => features[f] === true,
  );
  return {
    features,
    featuresEnabled,
  };
})
export default class StoreAccount extends React.Component {
  render() {
    return (
      <Flex
        align="center"
        justify="center"
        flexDirection="column"
        className={this.props.fitClassNames}
      >
        <Flex align="center" flexDirection="column" py={3}>
          <Box mb={3}>
            <h2>{t`Your features are active!`}</h2>
          </Box>
          <Flex align="center">
            {this.props.featuresEnabled &&
              this.props.featuresEnabled.map(f => {
                const feature = FEATURES[f];
                return (
                  feature && (
                    <Card p={4} mx={3}>
                      <Flex
                        align="center"
                        justify="center"
                        flexDirection="column"
                      >
                        <StoreIcon
                          name={feature.icon}
                          color={colors["success"]}
                        />
                        <Box my={2}>
                          <h3>{feature.name}</h3>
                        </Box>
                        {feature.docs && (
                          <a href={feature.docs} className="link">
                            {t`Learn how to use this`}
                          </a>
                        )}
                        {feature.docsRender && feature.docsRender()}
                      </Flex>
                    </Card>
                  )
                );
              })}
          </Flex>
        </Flex>
      </Flex>
    );
  }
}

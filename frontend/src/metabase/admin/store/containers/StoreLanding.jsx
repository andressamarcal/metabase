import React from "react";
import { Box, Flex } from "grid-styled";
import { t } from "c-3po";
import { connect } from "react-redux";
import { push } from "react-router-redux";

import Button from "metabase/components/Button";
import Link from "metabase/components/Link";
import Card from "metabase/components/Card";
import Text from "metabase/components/Text";
import StoreIcon from "../components/StoreIcon";

import fitViewport from "metabase/hoc/FitViewPort";

import FEATURES from "../lib/features";

@fitViewport
@connect(
  state => {
    const featuresEnabled = state.settings.values["premium_features"];
    const isActivated = Object.values(featuresEnabled).some(f => f === true);
    return {
      isActivated,
    };
  },
  { push },
)
export default class StoreLanding extends React.Component {
  componentWillMount() {
    if (this.props.isActivated) {
      this.props.push("/admin/store/account");
    }
  }
  render() {
    return (
      <Flex
        align="center"
        justify="center"
        className={this.props.fitClassNames}
      >
        <Box mr={4}>
          <StoreDetails />
        </Box>
        <Card>
          <Box w={400} p={3}>
            <h4>{t`Already have a key?`}</h4>
            <Link to="/admin/store/activate">
              <Button primary>{t`Activate a product`}</Button>
            </Link>
          </Box>
        </Card>
      </Flex>
    );
  }
}

class StoreDetails extends React.Component {
  render() {
    return (
      <Box>
        <Box my={3}>
          <h2>{t`Enterprise features`}</h2>
        </Box>
        <Box is="ul" className="text-measure">
          {Object.values(FEATURES).map(({ name, description, icon }) => (
            <Flex>
              <StoreIcon name={icon} />
              <Box ml={1}>
                <h2>{name}</h2>
                <Text>{description}</Text>
              </Box>
            </Flex>
          ))}
        </Box>
        <a
          href="https://metabase.com/offerings/"
          target="_blank"
          className="my2"
        >
          <Button>{t`Learn more`}</Button>
        </a>
      </Box>
    );
  }
}

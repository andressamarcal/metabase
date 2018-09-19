import React from "react";
import { Box, Flex } from "grid-styled";
import { t } from "c-3po";

import colors from "metabase/lib/colors";

import Button from "metabase/components/Button";
import Icon from "metabase/components/Icon";
import Link from "metabase/components/Link";
import Card from "metabase/components/Card";
import Text from "metabase/components/Text";

import fitViewport from "metabase/hoc/FitViewPort";

import { SettingsApi } from "metabase/services";

@fitViewport
class StoreApp extends React.Component {
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

const ICON_SIZE = 22;
const WRAPPER_SIZE = ICON_SIZE * 2.5;

const IconWrapper = ({ children }) => (
  <Flex
    align="center"
    justify="center"
    p={2}
    bg={colors["brand"]}
    color="white"
    w={WRAPPER_SIZE}
    style={{ borderRadius: 99, height: WRAPPER_SIZE }}
  >
    {children}
  </Flex>
);

class StoreDetails extends React.Component {
  render() {
    return (
      <Box>
        <Box my={3}>
          <h2>{t`Enterprise features`}</h2>
        </Box>
        <Box is="ul" className="text-measure">
          <Flex>
            <IconWrapper>
              <Icon name="lock" size={ICON_SIZE} />
            </IconWrapper>
            <Box ml={1}>
              <h2>{t`Row level permissions`}</h2>
              <Text>
                {t`Make sure you're showing the right people the right data with automatic and secure filters based on user attributes.`}
              </Text>
            </Box>
          </Flex>
          <Flex>
            <IconWrapper>
              <Icon name="star" size={ICON_SIZE} />
            </IconWrapper>
            <Box ml={1}>
              <h2>{t`Whitelabeling`}</h2>
              <Text>
                {t`Match Metabase to your brand with custom colors, your own logo and more.`}
              </Text>
            </Box>
          </Flex>
          <Flex>
            <IconWrapper>
              <Icon name="group" size={ICON_SIZE} />
            </IconWrapper>
            <Box ml={1}>
              <h2>{t`SSO`}</h2>
              <Text>
                {t`Provide easy login that works with your exisiting authentication infrastructure.`}
              </Text>
            </Box>
          </Flex>
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

@fitViewport
export class Account extends React.Component {
  render() {
    return (
      <Flex
        align="center"
        justify="center"
        flexDirection="column"
        className={this.props.fitClassNames}
      >
        <Box my={3}>
          <Icon name="check" size={64} color={colors["success"]} />
        </Box>
        <Box py={3}>
          <h2>{t`Your features are active!`}</h2>
        </Box>
      </Flex>
    );
  }
}

@fitViewport
export class Activate extends React.Component {
  state = {
    heading: t`Enter the token you recieved from the store`,
    error: false,
  };
  activate = async () => {
    const value = this._input.value;
    try {
      await SettingsApi.put({ key: "premium-embedding-token", value });
      // set window.location so we do a hard refresh
      window.location = "/admin/store/account";
    } catch (e) {
      console.error(e.data);
      this.setState({ error: true, heading: e.data });
    }
  };
  render() {
    return (
      <Flex
        align="center"
        justify="center"
        className={this.props.fitClassNames}
      >
        <Box>
          <Box my={3}>
            <h2
              className="text-centered"
              style={{ color: this.state.error ? colors["error"] : "inherit" }}
            >
              {this.state.heading}
            </h2>
          </Box>
          <input
            ref={ref => (this._input = ref)}
            type="text"
            className="input"
            placeholder="XXXX-XXXX-XXXX-XXXX"
          />
          <Button onClick={this.activate}>{t`Activate`}</Button>
        </Box>
      </Flex>
    );
  }
}

export default StoreApp;

using System.Globalization;
using System.Net;
using System.Windows;
using NearTransfer.Core;

namespace NearTransfer.Windows;

public partial class ManualAddressWindow : Window
{
    public ManualAddressWindow()
    {
        InitializeComponent();
        AddressBox.Focus();
    }

    public Peer? SelectedPeer { get; private set; }

    private void Connect_Click(object sender, RoutedEventArgs e)
    {
        if (!TryParseIpv4Literal(AddressBox.Text.Trim(), out var address))
        {
            ErrorText.Text = "Nhập một địa chỉ IPv4 hợp lệ.";
            AddressBox.Focus();
            return;
        }

        SelectedPeer = new Peer(
            address.ToString(),
            address,
            TransferProtocol.HttpPort,
            DateTimeOffset.UtcNow);
        DialogResult = true;
    }

    private void AddressBox_TextChanged(object sender, System.Windows.Controls.TextChangedEventArgs e)
    {
        if (AddressPlaceholder is not null)
        {
            AddressPlaceholder.Visibility = string.IsNullOrEmpty(AddressBox.Text)
                ? Visibility.Visible
                : Visibility.Collapsed;
        }
        if (ErrorText is not null)
        {
            ErrorText.Text = string.Empty;
        }
    }

    private static bool TryParseIpv4Literal(string value, out IPAddress address)
    {
        address = IPAddress.Any;
        var segments = value.Split('.');
        if (segments.Length != 4)
        {
            return false;
        }

        var octets = new byte[4];
        for (var index = 0; index < segments.Length; index++)
        {
            if (!byte.TryParse(
                    segments[index],
                    NumberStyles.None,
                    CultureInfo.InvariantCulture,
                    out octets[index]))
            {
                return false;
            }
        }

        address = new IPAddress(octets);
        return true;
    }

    private void Cancel_Click(object sender, RoutedEventArgs e) => DialogResult = false;
}
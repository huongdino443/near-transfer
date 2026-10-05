using NearTransfer.Core;
using System.ComponentModel;
using System.Runtime.CompilerServices;

namespace NearTransfer.Windows;

public sealed class PeerRowViewModel : INotifyPropertyChanged
{
    private Peer _peer;
    private bool _isSaved;
    private bool _isOnline;

    public PeerRowViewModel(Peer peer, bool isSaved = false, bool isOnline = true)
    {
        _peer = peer;
        _isSaved = isSaved;
        _isOnline = isOnline;
    }

    public event PropertyChangedEventHandler? PropertyChanged;

    public Peer Peer => _peer;
    public string Name => _peer.Name;
    public string AddressText => $"{_peer.Address}:{_peer.Port}";
    public bool IsSaved => _isSaved;
    public bool IsOnline => _isOnline;
    public string OnlineStatus => _isOnline ? "Trực tuyến" : "Ngoại tuyến";

    public void UpdatePeer(Peer peer)
    {
        _peer = peer;
        OnPropertyChanged(nameof(Peer));
        OnPropertyChanged(nameof(Name));
        OnPropertyChanged(nameof(AddressText));
    }

    public void SetSaved(bool isSaved)
    {
        if (_isSaved == isSaved)
        {
            return;
        }

        _isSaved = isSaved;
        OnPropertyChanged(nameof(IsSaved));
    }

    public void SetOnline(bool isOnline)
    {
        if (_isOnline == isOnline)
        {
            return;
        }

        _isOnline = isOnline;
        OnPropertyChanged(nameof(IsOnline));
        OnPropertyChanged(nameof(OnlineStatus));
    }

    private void OnPropertyChanged([CallerMemberName] string? propertyName = null) =>
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
}